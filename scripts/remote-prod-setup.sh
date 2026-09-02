#!/usr/bin/env bash
set -euo pipefail
REMOTE_DIR="/root/smart-meeting-java"
sed -i 's/\r$//' "$REMOTE_DIR/start-prod.sh"
mv -f "$REMOTE_DIR/start-prod.sh" "$REMOTE_DIR/start.sh"
chmod +x "$REMOTE_DIR/start.sh"

python3 - <<'PY'
from pathlib import Path
import re
p = Path("/etc/nginx/sites-available/meeting")
text = p.read_text()
snippet = """    location /meeting-admin/ {
        proxy_pass http://127.0.0.1:8766;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
    }
"""
if re.search(r'location /meeting-admin/', text):
    text = re.sub(
        r'    location /meeting-admin/ \{.*?\n    \}\n',
        snippet,
        text,
        count=1,
        flags=re.DOTALL,
    )
    p.write_text(text)
    print("nginx meeting-admin block repaired")
else:
    needle = "    location /scheduled-bot/"
    if needle not in text:
        raise SystemExit("cannot find scheduled-bot location")
    p.write_text(text.replace(needle, snippet + needle, 1))
    print("nginx meeting-admin block inserted")
PY

nginx -t
systemctl reload nginx
"$REMOTE_DIR/start.sh"
