#!/usr/bin/env bash
# Loads the freshly built plugin into a throw-away server, enables it, and (optionally) exercises
# every configured boss so that material/entity lookups are validated on that server version.
# Usage: smoke-test.sh <paper|purpur> <minecraft-version> <path-to-plugin-jar>
set -euo pipefail

SERVER_KIND="${1:?server kind required: paper|purpur}"
MC_VERSION="${2:?minecraft version required}"
JAR_PATH="${3:?plugin jar path required}"
WORKDIR="${WORKDIR:-smoke-${SERVER_KIND}-${MC_VERSION}}"
EXERCISE="${EXERCISE:-0}"
STARTUP_WAIT="${STARTUP_WAIT:-75}"

log() { printf '\n[smoke] %s\n' "$*"; }

log "preparing workspace ${WORKDIR} (${SERVER_KIND} ${MC_VERSION})"
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR/plugins"
cp "$JAR_PATH" "$WORKDIR/plugins/"

# Эмулируем сервер, обновившийся со старой версии: в lang.yml не хватает ключей,
# а часть значений прежние версии записали как "Missing!".
mkdir -p "$WORKDIR/plugins/BossLand"
printf 'items:\n  bell: "Missing!"\n' > "$WORKDIR/plugins/BossLand/lang.yml"

cd "$WORKDIR"

echo "eula=true" > eula.txt
{
  echo "online-mode=false"
  echo "level-type=minecraft\\:flat"
  echo "view-distance=4"
  echo "spawn-protection=0"
  # мини-боссы — враждебные мобы, в peaceful сервер удаляет их сразу после спавна
  echo "difficulty=hard"
  echo "spawn-monsters=true"
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
log "runtime: $(java -version 2>&1 | head -n1)"

# Console commands: reload the config, then spawn every configured boss so that the plugin has to
# build items, boss bars and mobs on this exact server version.
feed_commands() {
  sleep "$STARTUP_WAIT"
  echo "plugins"
  if [ "$EXERCISE" = "1" ]; then
    echo "bl-admin reload"
    sleep 2
    if [ -f plugins/BossLand/config.yml ]; then
      while read -r boss; do
        [ -n "$boss" ] || continue
        echo "bl-admin cspawn $boss 0 100 0 world"
        sleep 0.5
      done < <(python3 - <<'PY'
import re, sys
lines = open('plugins/BossLand/config.yml', encoding='utf-8', errors='replace').read().split('\n')
try:
    start = next(i for i, l in enumerate(lines) if l.startswith('bosses:'))
except StopIteration:
    sys.exit(0)
keys = []
for line in lines[start + 1:]:
    if line and not line.startswith(' '):
        break
    m = re.match(r'^  "?([A-Za-z][A-Za-z0-9_]*)"?:\s*$', line)
    if m:
        keys.append(m.group(1))
print('\n'.join(keys))
PY
      )
      sleep 8
      # без игроков чанк со спавном может выгрузиться, и тогда мини-босс «исчезает» вместе с ним
      echo "forceload add 0 0"
      sleep 2
      # mini-bosses: explicit type, elite-tier type and a random one
      echo "bl-admin miniboss ZOMBIE"
      sleep 2
      echo "bl-admin miniboss WITCH"
      sleep 2
      echo "bl-admin miniboss"
      sleep 3
      # настоящая смерть мини-босса: проверяет обработчик onEntityDeath
      # (раньше он падал с NullPointerException и лут не выдавался)
      echo "bl-admin killMiniBosses world"
      sleep 3
      # зона сражения: мини-босс без игроков должен исчезнуть через despawnDelaySeconds
      python3 - <<'PY'
import re, pathlib
path = pathlib.Path('plugins/BossLand/config.yml')
text = path.read_text(encoding='utf-8')
text = re.sub(r'(?m)^  despawnDelaySeconds: .*$', '  despawnDelaySeconds: 3', text)
path.write_text(text, encoding='utf-8')
PY
      echo "bl-admin reload"
      sleep 2
      echo "bl-admin miniboss ZOMBIE"
      sleep 8
      echo "forceload remove 0 0"
      # самопроверка: дроп осколков + сборка предметов призыва в сетке крафта
      echo "bl-admin selftest"
      sleep 3
      echo "bl-admin killBosses world"
      sleep 4
    fi
  fi
  echo "stop"
}

log "starting server"
feed_commands | timeout 420 java -Xms512M -Xmx1024M -Dcom.mojang.eula.agree=true -jar server.jar --nogui > server.log 2>&1 || true

log "plugin / lifecycle log lines"
{ grep -iE 'bossland|Done \(|[Mm]ini-boss' server.log | tail -n 45 || true; } | sed 's/^/[smoke]   /'
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
if grep -qE 'Error occurred while enabling BossLand|Could not load .*[Bb]ossland' <<< "$PRE_STOP"; then
  echo '::error::BossLand threw during startup'
  status=1
fi

if [ "$EXERCISE" = "1" ]; then
  log "runtime exceptions (boss spawning exercise)"
  EXC="$(grep -nE 'Exception|Caused by:' <<< "$PRE_STOP" | grep -viE 'PaperVersionFetcher|Error obtaining version information|Failed to check for updates' || true)"
  if [ -n "$EXC" ]; then
    printf '%s\n' "$EXC" | head -n 40 | sed 's/^/[smoke]   /'
    echo '::error::exceptions while exercising bosses'
    status=1
  else
    log "no runtime exceptions during the boss exercise"
  fi
  log "spawned bosses seen in log"
  { grep -icE 'Спавн босса|Спавн|спавн' server.log || true; } | sed 's/^/[smoke]   lines matching: /'

  # мини-боссы должны реально появиться (3 вызова команды в feed_commands)
  MINI_COUNT="$(grep -c 'Мини-босс заспавнен' server.log || true)"
  log "mini-bosses spawned: ${MINI_COUNT}"
  if [ "${MINI_COUNT:-0}" -lt 2 ]; then
    echo '::error::mini-bosses were not spawned by bl-admin miniboss'
    status=1
  fi

  # самопроверка должна завершиться без ошибок
  if grep -q 'САМОПРОВЕРКА: OK' server.log; then
    log "self-test passed: $(grep -o 'САМОПРОВЕРКА: OK ([0-9]* проверок)' server.log | tail -1)"
  else
    echo '::error::BossLand self-test failed (shard names / crafting)'
    grep -E 'САМОПРОВЕРКА|✗' server.log | tail -n 20 | sed 's/^/[smoke]   /'
    status=1
  fi

  # отладочный вывод не должен попадать в консоль сервера
  DEBUG_LINES="$(grep -nE 'Make Boss|Block Cancel Land|Error: No valid drops found|getIntFromString' <<< "$PRE_STOP" || true)"
  if [ -n "$DEBUG_LINES" ]; then
    echo '::error::debug output leaked into the server console'
    printf '%s\n' "$DEBUG_LINES" | head -n 5 | while IFS= read -r line; do echo "::error::DEBUG: $line"; done
    status=1
  fi
  log "no debug output in the server console"

  # мини-босс, оставшийся без игроков, должен исчезнуть
  if grep -q 'исчез:' server.log; then
    log "combat zone: $(grep -o 'Мини-босс .* исчез:.*' server.log | tail -1)"
  else
    echo '::error::mini-boss was not despawned when no players were left in the combat zone'
    grep -iE 'Мини-босс|despawn' server.log | tail -n 6 | while IFS= read -r line; do echo "::error::ZONE: $line"; done
    status=1
  fi

  # смерть мини-босса должна отработать штатно и выдать награду
  if grep -q 'Мини-босс WITCH повержен' server.log; then
    log "mini-boss death handled: $(grep -o 'Мини-босс WITCH повержен' server.log | tail -1)"
  else
    echo '::error::mini-boss death handler did not run (see NullPointerException in onEntityDeath)'
    grep -inE 'Мини-босс|мини-босс|NullPointer|Could not pass' server.log | tail -n 6 \
      | while IFS= read -r line; do echo "::error::MINI: $line"; done
    status=1
  fi

  # старый lang.yml должен быть дополнен ключами и вычищен от "Missing!"
  if grep -q 'Missing!' plugins/BossLand/lang.yml 2>/dev/null; then
    echo '::error::lang.yml still contains "Missing!" placeholders'
    grep -n 'Missing!' plugins/BossLand/lang.yml | head -n 5 | sed 's/^/[smoke]   /'
    status=1
  fi
  if ! grep -q '^miniboss:' plugins/BossLand/lang.yml 2>/dev/null; then
    echo '::error::lang.yml was not extended with the new keys'
    status=1
  fi
  log "old lang.yml was merged with the bundled defaults"

  # и переведённый конфиг должен лежать в plugins/BossLand
  if ! grep -q 'Герцог Гастов' plugins/BossLand/config.yml 2>/dev/null; then
    echo '::error::translated config was not generated'
    status=1
  fi
  if ! grep -q 'blockThreshold: 200' plugins/BossLand/config.yml 2>/dev/null; then
    echo '::error::new miniboss section missing from the generated config'
    status=1
  fi
  if ! grep -q 'despawnWhenAlone' plugins/BossLand/config.yml 2>/dev/null; then
    echo '::error::combat zone keys missing from the generated config'
    status=1
  fi
  if ! grep -q 'leaveZone' plugins/BossLand/lang.yml 2>/dev/null; then
    echo '::error::zone messages missing from the generated lang.yml'
    status=1
  fi
  log "generated config contains translated names and the miniboss section"
fi

if [ "$status" -eq 0 ]; then
  log "smoke test passed on ${SERVER_KIND} ${MC_VERSION}"
else
  log "smoke test FAILED on ${SERVER_KIND} ${MC_VERSION}"
  { grep -iE 'bossland|exception|error|Caused by' server.log | tail -n 60 || true; } | sed 's/^/[smoke]   /'
fi
exit "$status"
