#!/usr/bin/env bash
set -euo pipefail

OC=/home/alan/.npm-global/bin/openclaw
CFG=/home/alan/.openclaw/openclaw.json
SRC=/home/alan/.openclaw-clone-boss/openclaw.json.last-good

cp "$CFG" "$CFG.broken-$(date +%Y%m%d-%H%M%S)"
cp "$SRC" "$CFG"

python3 <<'PY'
import json
p = "/home/alan/.openclaw/openclaw.json"
with open(p, encoding="utf-8") as f:
    c = json.load(f)
auth = c.setdefault("gateway", {}).setdefault("auth", {})
scopes = auth.setdefault("scopes", [])
for s in ("operator.read", "operator.write"):
    if s not in scopes:
        scopes.append(s)
auth["scopes"] = scopes
with open(p, "w", encoding="utf-8") as f:
    json.dump(c, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("restored from clone-boss")
print("scopes=", scopes)
print("channels=", list(c.get("channels", {}).keys()))
print("agents=", [a.get("id") for a in c.get("agents", {}).get("list", [])])
PY

"$OC" config validate
systemctl --user restart openclaw-gateway.service
sleep 4
echo "=== gateway status ==="
"$OC" gateway status
echo "=== status summary ==="
"$OC" status | head -35
