#!/usr/bin/env bash
set -euo pipefail

CODE="${1:?usage: exchange-feishu-oauth-code.sh CODE}"
APP_ID="${FEISHU_APP_ID:-cli_a9742c7795795bc2}"
APP_SECRET="${FEISHU_APP_SECRET:-yNKh96SePvhk4h3b1Z1Rnjw3RZ6fivIj}"
REDIRECT="${MEETING_ADMIN_FEISHU_REDIRECT_URI:-https://oa.qdyhjz.cn/meeting-admin/admin/oauth/feishu/callback}"
ADMIN_TOKEN="${ADMIN_TOKEN:-dev-admin-token}"
MINUTE_TOKEN="${MINUTE_TOKEN:-obcnhau3923882nx59ur15i1}"

echo "=== v2 token exchange ==="
TOK=$(curl -s -X POST 'https://open.feishu.cn/open-apis/authen/v2/oauth/token' \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d "$(python3 - <<PY
import json, os
print(json.dumps({
  'grant_type': 'authorization_code',
  'client_id': os.environ['APP_ID'],
  'client_secret': os.environ['APP_SECRET'],
  'code': os.environ['CODE'],
  'redirect_uri': os.environ['REDIRECT'],
}))
PY
)")

export TOK
python3 - <<'PY'
import json, os
d = json.loads(os.environ['TOK'])
print('code', d.get('code'), 'msg', d.get('msg') or d.get('error_description', ''))
print('has_access_token', bool(d.get('access_token')))
print('has_refresh_token', bool(d.get('refresh_token')))
rt = d.get('refresh_token') or ''
if rt:
    open('/tmp/feishu-minutes-refresh.token', 'w').write(rt)
PY

if [ ! -f /tmp/feishu-minutes-refresh.token ]; then
  echo "no refresh_token, abort"
  exit 1
fi

RT=$(tr -d '\n\r' < /tmp/feishu-minutes-refresh.token)
echo "=== save to admin config ==="
SAVE=$(curl -s -X PUT \
  "https://oa.qdyhjz.cn/meeting-admin/api/v1/admin/system-config/entries/meeting.feishu.minutes.user-refresh-token" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "$(RT="$RT" python3 - <<'PY'
import json, os
print(json.dumps({'valueJson': os.environ['RT']}))
PY
)" -k)
python3 -c "import json,sys; d=json.load(sys.stdin); print('save_code', d.get('code'), 'msg', d.get('message',''))" <<< "$SAVE"

echo "=== trigger runtime reload ==="
curl -s -X POST "https://oa.qdyhjz.cn/meeting-admin/api/v1/admin/system-config/runtime-reload" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}" -k | python3 -c "import json,sys; print(json.load(sys.stdin))"

echo "=== verify media download ==="
AT=$(curl -s -X POST 'https://open.feishu.cn/open-apis/authen/v2/oauth/token' \
  -H 'Content-Type: application/json' \
  -d "$(python3 - <<PY
import json, os
print(json.dumps({
  'grant_type': 'refresh_token',
  'client_id': os.environ['APP_ID'],
  'client_secret': os.environ['APP_SECRET'],
  'refresh_token': os.environ['RT'],
}))
PY
)" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))")

M=$(curl -s -H "Authorization: Bearer ${AT}" \
  "https://open.feishu.cn/open-apis/minutes/v1/minutes/${MINUTE_TOKEN}/media")
export M
python3 - <<'PY'
import json, os
d = json.loads(os.environ['M'])
print('media_code', d.get('code'), d.get('msg'))
print('has_download_url', bool(d.get('data', {}).get('download_url')))
PY
