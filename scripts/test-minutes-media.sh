#!/usr/bin/env bash
set -euo pipefail
RT=$(tr -d '\n\r' < /root/smart-meeting-java/secrets/feishu-minutes-refresh.token)
APP_ID="${FEISHU_APP_ID:-cli_a9742c7795795bc2}"
APP_SECRET="${FEISHU_APP_SECRET:-yNKh96SePvhk4h3b1Z1Rnjw3RZ6fivIj}"
MINUTE_TOKEN="${1:-obcnhau3923882nx59ur15i1}"

TOK=$(curl -s -X POST 'https://open.feishu.cn/open-apis/authen/v2/oauth/token' \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d "$(python3 - <<PY
import json, os
print(json.dumps({
  'grant_type': 'refresh_token',
  'client_id': os.environ['APP_ID'],
  'client_secret': os.environ['APP_SECRET'],
  'refresh_token': '''$RT''',
}))
PY
)")

CODE=$(echo "$TOK" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code', -1))")
echo "refresh_code=$CODE"
if [ "$CODE" != "0" ]; then
  echo "$TOK" | head -c 300
  exit 1
fi

AT=$(echo "$TOK" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
M=$(curl -s -H "Authorization: Bearer $AT" \
  "https://open.feishu.cn/open-apis/minutes/v1/minutes/${MINUTE_TOKEN}/media")
echo "$M" | python3 -c "import sys,json; d=json.load(sys.stdin); print('media_code', d.get('code'), d.get('msg')); print('has_download_url', bool(d.get('data',{}).get('download_url')))"
