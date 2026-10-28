#!/usr/bin/env bash
# Loads the freshly built plugin into a throw-away server and checks that it enables cleanly.
# Usage: smoke-test.sh <paper|purpur> <minecraft-version> <path-to-plugin-jar>
set -euo pipefail

SERVER_KIND="${1:?server kind required: paper|purpur}"
MC_VERSION="${2:?minecraft version required}"
JAR_PATH="${3:?plugin jar path required}"
WORKDIR="${WORKDIR:-smoke-${SERVER_KIND}-${MC_VERSION}}"

log() { printf '\n[smoke] %s\n' "$*"; }

log "preparing workspace ${WORKDIR} (${SERVER_KIND} ${MC_VERSION})"
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

SERVER_URL=""
case "$SERVER_KIND" in
  purpur)
    log "resolving Purpur ${MC_VERSION}"
    SERVER_URL="https://api.purpurmc.org/v2/purpur/${MC_VERSION}/latest/download"
    ;;
  paper)
    log "resolving Paper ${MC_VERSION}"
    V3="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
    if V3_JSON="$(curl -fsSL --max-time 30 "$V3" 2>/dev/null)"; then
      SERVER_URL="$(jq -r 'if type=="array" then .[-1] else .builds[-1] end
                           | (.downloads // {})
                           | (if type=="object" then (to_entries[0].value.url // empty) else . end)' \
                      <<< "$V3_JSON" 2>/dev/null || true)"
    fi
    if [ -z "$SERVER_URL" ]; then
      V2="https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds"
      if V2_JSON="$(curl -fsSL --max-time 30 "$V2" 2>/dev/null)"; then
        BUILD="$(jq -r '.builds[-1].build' <<< "$V2_JSON" 2>/dev/null || true)"
        if [ -n "$BUILD" ] && [ "$BUILD" != "null" ]; then
          SERVER_URL="https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds/${BUILD}/downloads/paper-${MC_VERSION}-${BUILD}.jar"
        fi
      fi
    fi
    ;;
  *)
    echo "[smoke] ERROR: unknown server kind '${SERVER_KIND}'" >&2
    exit 1
    ;;
esac

if [ -z "$SERVER_URL" ]; then
  echo "[smoke] ERROR: could not resolve a ${SERVER_KIND} ${MC_VERSION} download url" >&2
  exit 1
fi

log "downloading ${SERVER_URL}"
curl -fsSL --retry 3 -o server.jar "$SERVER_URL"
ls -lh server.jar
log "$(java -version 2>&1 | head -n1)"

log "starting server"
( sleep 75; echo "plugins"; echo "stop" ) \
  | timeout 360 java -Xms512M -Xmx1024M -Dcom.mojang.eula.agree=true -jar server.jar --nogui > server.log 2>&1 || true

log "plugin / lifecycle log lines"
{ grep -iE 'bossland|Done \(' server.log | tail -n 40 || true; } | sed 's/^/[smoke]   /'
log "plugins command output"
{ sed -n '/Plugins (/,$p' server.log | head -n 10 || true; } | sed 's/^/[smoke]   /'

status=0
grep -q 'Done (' server.log || { echo '::error::server never finished starting'; status=1; }
grep -qi 'Enabling BossLand' server.log || { echo '::error::BossLand was never enabled'; status=1; }

# Everything the plugin did *before* the shutdown started: a disable line here means it crashed.
PRE_STOP="$(sed '/Stopping the server/,$d' server.log || true)"
if grep -qi 'Disabling BossLand' <<< "$PRE_STOP"; then
  echo '::error::BossLand disabled itself before shutdown'
  status=1
fi
if grep -qE 'Error occurred while enabling BossLand|Could not load .*[Bb]ossland|Caused by: java\.' <<< "$PRE_STOP"; then
  echo '::error::BossLand threw during startup'
  status=1
fi
if grep -qiE 'unsupported api version|legacy plugin|is not compatible' <<< "$PRE_STOP"; then
  echo '::warning::BossLand reported an API compatibility problem'
fi

if [ "$status" -eq 0 ]; then
  log "smoke test passed on ${SERVER_KIND} ${MC_VERSION}"
else
  log "smoke test FAILED on ${SERVER_KIND} ${MC_VERSION}"
  { grep -iE 'bossland|exception|error|Caused by' server.log | tail -n 60 || true; } | sed 's/^/[smoke]   /'
fi
exit "$status"
