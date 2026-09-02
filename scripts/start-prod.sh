#!/usr/bin/env bash
# Production start on Aliyun 39.97.61.212 (oa.qdyhjz.cn)
# meeting-server :8765  → nginx /meeting-server/
# meeting-admin  :8766  → nginx /meeting-admin/
#
# Usage (on server):
#   /root/smart-meeting-java/start.sh
#
# Env overrides: SPRING_PROFILES_ACTIVE, AGENDA_MATERIAL_DIR, MEETING_SERVER_URL
set -euo pipefail

APP_DIR="/root/smart-meeting-java"
JAVA_BIN="/root/.sdkman/candidates/java/current/bin/java"
FRP_DIR="/root/frp/frp_0.61.1_linux_amd64"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
export AGENDA_MATERIAL_DIR="${AGENDA_MATERIAL_DIR:-$APP_DIR/data/agenda-materials}"
export MEETING_AUDIO_CACHE_DIR="${MEETING_AUDIO_CACHE_DIR:-$APP_DIR/data/audio}"
export MEETING_SERVER_URL="${MEETING_SERVER_URL:-http://127.0.0.1:8765/meeting-server}"
export MEETING_NOTIFY_BOT_URL="${MEETING_NOTIFY_BOT_URL:-http://127.0.0.1:8764/scheduled-bot}"

# 管理后台飞书 OAuth（与 meeting-server meeting.feishu 同一应用）
export FEISHU_APP_ID="${FEISHU_APP_ID:-cli_a9742c7795795bc2}"
export FEISHU_APP_SECRET="${FEISHU_APP_SECRET:-yNKh96SePvhk4h3b1Z1Rnjw3RZ6fivIj}"
export MEETING_ADMIN_FEISHU_OAUTH_ENABLED="${MEETING_ADMIN_FEISHU_OAUTH_ENABLED:-true}"

# 妙记 File B 下载（user_access_token，与飞书 API 调试台一致）
# export FEISHU_MINUTES_USER_ACCESS_TOKEN='u-...'    # 可选：调试台临时 token（约 2h）
if [ -f "$APP_DIR/secrets/feishu-minutes-refresh.token" ]; then
  export FEISHU_MINUTES_USER_REFRESH_TOKEN="$(tr -d '\n\r' < "$APP_DIR/secrets/feishu-minutes-refresh.token")"
fi

cd "$APP_DIR"
mkdir -p data/audio data/agenda-materials logs

if ss -lntp 2>/dev/null | grep -q ':8765 .*frps'; then
  echo "[deploy] 8765 held by frps — stopping frps so meeting-server can bind locally"
  pkill -f '/frps -c' || pkill -x frps || true
  sleep 2
fi

if pgrep -f 'meeting-server-0.1.0.jar' >/dev/null 2>&1; then
  echo "[deploy] stopping old meeting-server"
  pkill -f 'meeting-server-0.1.0.jar' || true
fi
if pgrep -f 'meeting-admin-server-0.1.0.jar' >/dev/null 2>&1; then
  echo "[deploy] stopping old meeting-admin-server"
  pkill -f 'meeting-admin-server-0.1.0.jar' || true
fi
sleep 2

echo "[deploy] starting meeting-server (profile=$SPRING_PROFILES_ACTIVE)"
nohup "$JAVA_BIN" -Xms512m -Xmx1536m -jar "$APP_DIR/meeting-server-0.1.0.jar" \
  --spring.profiles.active="$SPRING_PROFILES_ACTIVE" \
  > "$APP_DIR/logs/meeting-server.log" 2>&1 &

echo "[deploy] starting meeting-admin-server"
nohup "$JAVA_BIN" -Xms256m -Xmx768m -jar "$APP_DIR/meeting-admin-server-0.1.0.jar" \
  --spring.profiles.active="$SPRING_PROFILES_ACTIVE" \
  > "$APP_DIR/logs/meeting-admin-server.log" 2>&1 &

# 勿重启 frps：其 proxy 占 8765 会与 meeting-server 冲突；生产直连 Java 后 frps 保持停止

echo "[deploy] waiting for meeting-server health"
for i in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:8765/meeting-server/api/v1/health" >/dev/null 2>&1; then
    echo "[deploy] meeting-server health OK"
    break
  fi
  sleep 2
done
curl -sf "http://127.0.0.1:8765/meeting-server/api/v1/health" || {
  echo "[deploy] meeting-server health FAILED"
  tail -n 80 "$APP_DIR/logs/meeting-server.log"
  exit 1
}

for i in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:8766/meeting-admin/actuator/health" >/dev/null 2>&1 \
    || curl -sf "http://127.0.0.1:8766/meeting-admin/api/v1/admin/integrations/health" >/dev/null 2>&1; then
    echo "[deploy] meeting-admin health OK"
    break
  fi
  sleep 2
done

echo "[deploy] done. processes:"
pgrep -af 'meeting-server-0.1.0.jar|meeting-admin-server-0.1.0.jar|frps' || true
