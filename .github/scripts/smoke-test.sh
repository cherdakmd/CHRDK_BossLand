#!/usr/bin/env bash
# Loads the freshly built plugin into a throw-away Paper server and checks that it enables cleanly.
# Usage: smoke-test.sh <path-to-plugin-jar> [minecraft-version]
set -euo pipefail

JAR_PATH="${1:?plugin jar path required}"
MC_VERSION="${2:-1.21.5}"
WORKDIR="${WORKDIR:-smoke}"

log() { printf '\n[smoke] %s\n' "$*"; }

log "preparing workspace ${WORKDIR}"
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR/plugins"
cp "$JAR_PATH" "$WORKDIR/plugins/"
cd "$WORKDIR"

echo "eula=true" > eula.txt
{
  echo "online-mode=false"
  echo "level-type=minecraft\\:flat"
  echo "view-distance=4"
  echo "spawn-protection=0"
  echo "max-players=2"
  echo "sync-chunk-writes=false"
} > server.properties

PAPER_URL=""
log "resolving Paper ${MC_VERSION} download url"

# Fill v3 API (current)
V3="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
if V3_JSON="$(curl -fsSL --max-time 30 "$V3" 2>/dev/null)"; then
  log "fill v3 responded: ${V3_JSON:0:240}"
  log "last build keys: $(jq -r 'if type=="array" then .[-1] else . end | keys_unsorted | join(",")' <<< "$V3_JSON" 2>/dev/null || echo '?')"
  PAPER_URL="$(jq -r 'if type=="array" then .[-1] else .builds[-1] end
                      | (.downloads // {})
                      | (if type=="object" then (to_entries[0].value.url // empty) else . end)' \
                 <<< "$V3_JSON" 2>/dev/null || true)"
fi

# Legacy v2 API fallback
if [ -z "$PAPER_URL" ]; then
  V2="https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds"
  if V2_JSON="$(curl -fsSL --max-time 30 "$V2" 2>/dev/null)"; then
    log "api v2 responded"
    BUILD="$(echo "$V2_JSON" | jq -r '.builds[-1].build' 2>/dev/null || true)"
    if [ -n "$BUILD" ] && [ "$BUILD" != "null" ]; then
      PAPER_URL="https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds/${BUILD}/downloads/paper-${MC_VERSION}-${BUILD}.jar"
    fi
  else
    log "api v2 request failed"
  fi
fi

if [ -z "$PAPER_URL" ]; then
  echo "[smoke] ERROR: could not resolve a Paper download url" >&2
  exit 1
fi
log "downloading ${PAPER_URL}"
curl -fsSL --retry 3 -o paper.jar "$PAPER_URL"
ls -lh paper.jar

log "starting server"
( sleep 60; echo "plugins"; echo "stop" ) \
  | timeout 330 java -Xms512M -Xmx1024M -Dcom.mojang.eula.agree=true -jar paper.jar --nogui > server.log 2>&1 || true

log "plugin / lifecycle log lines"
{ grep -iE 'bossland|Done \(' server.log | tail -n 40 || true; } | sed 's/^/[smoke]   /'
log "plugins command output"
{ sed -n '/Plugins (/,$p' server.log | head -n 10 || true; } | sed 's/^/[smoke]   /' 

status=0
grep -q 'Done (' server.log || { echo '::error::server never finished starting'; status=1; }
grep -qiE 'Enabling BossLand' server.log || { echo '::error::BossLand was not enabled'; status=1; }
if grep -qiE 'Disabling BossLand|Error occurred while enabling BossLand|Could not load .*BossLand' server.log; then
  echo '::error::BossLand failed to load, see server.log'
  sed -n '/BossLand/,/^$/p' server.log | head -n 60
  status=1
fi

if [ "$status" -eq 0 ]; then
  log "smoke test passed"
else
  log "smoke test FAILED"
  { grep -iE 'bossland|exception|error' server.log | tail -n 60 || true; } | sed 's/^/[smoke]   /'
  { tail -n 60 server.log || true; } | sed 's/^/[smoke]   /' 
fi
exit "$status"
