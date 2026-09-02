#!/usr/bin/env bash
# 在 OpenClaw Gateway 主机（39.97.61.212）上运行：允许跨机 token 连接保留 operator scopes。
# 背景：OpenClaw 2026.x 对无 device identity 的远程 token 连接会 clearUnboundScopes()。
#
# 用法（Gateway 主机 root）：
#   bash scripts/openclaw-gateway-enable-remote-scopes.sh [--profile jqclaw]
#
# 改完后：openclaw --profile <profile> gateway restart

set -euo pipefail

PROFILE="${1:-jqclaw}"
if [[ "${1:-}" == "--profile" ]]; then
  PROFILE="${2:-jqclaw}"
fi

CONFIG="${HOME}/.openclaw-${PROFILE}/openclaw.json"
if [[ ! -f "$CONFIG" && -f "${HOME}/.openclaw/openclaw.json" ]]; then
  CONFIG="${HOME}/.openclaw/openclaw.json"
fi
if [[ ! -f "$CONFIG" ]]; then
  echo "找不到 openclaw.json（profile=$PROFILE）" >&2
  exit 1
fi

echo "Patching $CONFIG ..."
python3 - "$CONFIG" <<'PY'
import json, sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    cfg = json.load(f)
gw = cfg.setdefault("gateway", {})
auth = gw.setdefault("auth", {})
scopes = auth.setdefault("scopes", [])
for s in ("operator.read", "operator.write"):
    if s not in scopes:
        scopes.append(s)
auth["scopes"] = scopes
with open(path, "w", encoding="utf-8") as f:
    json.dump(cfg, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("gateway.auth.scopes =", auth["scopes"])
PY

if command -v openclaw >/dev/null 2>&1; then
  openclaw --profile "$PROFILE" config validate
  openclaw --profile "$PROFILE" gateway restart
  echo "Gateway restarted (profile=$PROFILE)."
else
  echo "openclaw CLI 不在 PATH；请手动 restart gateway。"
fi
