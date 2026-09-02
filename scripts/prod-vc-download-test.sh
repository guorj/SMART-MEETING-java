#!/usr/bin/env bash
set -euo pipefail

MEETING_ID="${1:-3ebbeda1-55c9-470e-b3b7-3632e6a3020a}"
MINUTE_TOKEN="${2:-obcnlx3y8hy5jmgj85b19xr6}"
ADMIN_TOKEN="${ADMIN_TOKEN:-dev-admin-token}"
INTERNAL_TOKEN="${INTERNAL_TOKEN:-dev-internal-reload}"
BASE="https://oa.qdyhjz.cn/meeting-admin/api/v1/admin"

echo "=== 1. refresh token config (audit) ==="
curl -sf -H "X-Admin-Token: ${ADMIN_TOKEN}" \
  "${BASE}/system-config/audit?configKey=meeting.feishu.minutes.user-refresh-token&limit=3" \
  | python3 -c "import sys,json; rows=json.load(sys.stdin).get('data',[]); print('audit_rows=',len(rows)); print('latest=', rows[0].get('changedAt') if rows else 'none')" 2>/dev/null || echo "audit check failed"

echo "=== 2. v2 oauth in jar ==="
unzip -p /root/smart-meeting-java/meeting-server-0.1.0.jar \
  BOOT-INF/classes/com/smartmeeting/service/FeishuMinutesUserTokenProvider.class \
  | strings | grep -E 'v2/oauth|user_access_token refreshed' || echo "v2 strings NOT FOUND"

echo "=== 3. jar mtime ==="
ls -la /root/smart-meeting-java/meeting-server-0.1.0.jar

echo "=== 4. meeting before ==="
curl -sf -H "X-Admin-Token: ${ADMIN_TOKEN}" \
  "${BASE}/meetings/${MEETING_ID}" \
  | python3 -c "import sys,json; m=json.load(sys.stdin).get('data',{}).get('meeting',{}); print('status=',m.get('status'),'token=',m.get('vcMinuteToken'),'audio=',m.get('audioPath'))"

echo "=== 5. vc.recording-enabled in schema ==="
curl -sf -H "X-Admin-Token: ${ADMIN_TOKEN}" "${BASE}/system-config/schema" \
  | grep -o 'meeting.vc.recording-enabled' | head -1 || true

echo "=== 6. reload runtime ==="
curl -sf -X POST -H "X-Admin-Token: ${ADMIN_TOKEN}" \
  "${BASE}/system-config/reload-runtime"
echo ""

echo "=== 7. trigger regenerate minutes via webhook (File B download) ==="
curl -sf -X POST \
  -H "Content-Type: application/json" \
  -d "{\"header\":{\"event_type\":\"im.message.receive_v1\",\"event_id\":\"vc-download-test-$(date +%s)\"},\"event\":{\"message\":{\"message_type\":\"text\",\"content\":\"{\\\"text\\\":\\\"重新生成 会议ID:${MEETING_ID}\\\"}\",\"chat_id\":\"admin_vc_test\"},\"sender\":{\"sender_id\":{\"user_id\":\"admin_vc_test\"}}}}" \
  "http://127.0.0.1:8765/meeting-server/api/v1/feishu/webhook"
echo ""

echo "=== 8. wait for download (90s) ==="
sleep 90

echo "=== 9. meeting after ==="
curl -sf -H "X-Admin-Token: ${ADMIN_TOKEN}" \
  "${BASE}/meetings/${MEETING_ID}" \
  | python3 -c "import sys,json; m=json.load(sys.stdin).get('data',{}).get('meeting',{}); print('status=',m.get('status'),'token=',m.get('vcMinuteToken'),'audio=',m.get('audioPath'))"

echo "=== 10. pcm files ==="
find /root/smart-meeting-java/data/audio -name "${MEETING_ID}_vc*" -ls 2>/dev/null || echo "no vc pcm yet"

echo "=== 11. recent logs ==="
LOG="/root/smart-meeting-java/logs/meeting-server/smart-meeting.log"
grep -E 'FeishuMinutes|user_access_token refreshed|getMediaDownloadUrl|downloadAndTranscode|2091005|File B|AudioSourceResolver|downloaded:|transcoded|regenerate' \
  "$LOG" /root/smart-meeting-java/logs/meeting-server.log 2>/dev/null | tail -60
