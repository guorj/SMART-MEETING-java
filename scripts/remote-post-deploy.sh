#!/usr/bin/env bash
set -euo pipefail
MYSQL=(mysql -h60.205.1.17 -uintelligence -p'intelligence@2026' intelligence)

"${MYSQL[@]}" < /tmp/v0.28-preset-minute-skill.sql
"${MYSQL[@]}" -e "SHOW COLUMNS FROM int_meeting_type_preset LIKE 'minute_skill_name';"
"${MYSQL[@]}" -e "SELECT code, minute_skill_name FROM int_meeting_type_preset ORDER BY code;"
"${MYSQL[@]}" -e "INSERT INTO int_meeting_system_config (config_key, category, value_json, description, updated_at)
  VALUES ('meeting.minute.skill-generation-enabled', 'minute', 'true', 'SKILL template inject LLM Prompt', NOW())
  ON DUPLICATE KEY UPDATE description=VALUES(description), updated_at=NOW();"

echo "[post-deploy] skills dir:"
ls -la /root/smart-meeting-java/skills/

/root/smart-meeting-java/start.sh

sleep 3
echo "[post-deploy] processes:"
pgrep -af 'meeting-.*jar' || true
curl -sf http://127.0.0.1:8765/meeting-server/api/v1/health; echo
curl -sf http://127.0.0.1:8766/meeting-admin/actuator/health; echo
