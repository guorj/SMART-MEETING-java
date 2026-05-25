#!/usr/bin/env bash
# Smart Meeting dev launcher: meeting-server (:8765) / meeting-admin-server (:8766)
# Usage:
#   ./scripts/start-dev.sh              # interactive menu
#   ./scripts/start-dev.sh server
#   ./scripts/start-dev.sh admin
#   ./scripts/start-dev.sh both
#   ./scripts/start-dev.sh both --local-deps
#   ./scripts/start-dev.sh deps         # docker mysql+redis only
#   ./scripts/start-dev.sh compile
#
# Maven: 默认使用 PATH 中的本机 mvn（WSL 如 /usr/bin/mvn）；仅当无 mvn 或 USE_MVNW=1 时用 ./mvnw
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TARGET="menu"
WITH_LOCAL_DEPS=false
SKIP_COMPILE=false
SKIP_DEP_CHECK=false

usage() {
  sed -n '2,12p' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-deps) WITH_LOCAL_DEPS=true ;;
    --skip-compile) SKIP_COMPILE=true ;;
    --skip-dep-check) SKIP_DEP_CHECK=true ;;
    -h|--help) usage; exit 0 ;;
    server|admin|both|deps|compile|menu) TARGET="$1" ;;
    0) exit 0 ;;
    1) TARGET=menu ;;
    2) TARGET=server ;;
    3) TARGET=admin ;;
    4) TARGET=both ;;
    5) WITH_LOCAL_DEPS=true; TARGET=both ;;
    6) TARGET=deps ;;
    7) TARGET=compile ;;
    *)
      echo "未知参数: $1 （示例: ./scripts/start-dev.sh server --skip-compile）" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

DB_HOST="${DB_HOST:-60.205.1.17}"
DB_PORT="${DB_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-60.205.1.17}"
REDIS_PORT="${REDIS_PORT:-6379}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
ADMIN_TOKEN="${ADMIN_TOKEN:-dev-admin-token}"
INTERNAL_RELOAD_TOKEN="${INTERNAL_RELOAD_TOKEN:-dev-internal-reload}"
MEETING_SERVER_URL="${MEETING_SERVER_URL:-http://127.0.0.1:8765}"

export SPRING_PROFILES_ACTIVE ADMIN_TOKEN INTERNAL_RELOAD_TOKEN MEETING_SERVER_URL

banner() {
  echo ""
  echo "========================================"
  echo "  Smart Meeting Dev Launcher"
  echo "  Root: $ROOT"
  echo "========================================"
  echo ""
}

MVN_WRAPPER_BIN="${HOME}/.m2/wrapper/dists/apache-maven-3.9.6/bin/mvn"
MVN_EXEC=""

ensure_maven_wrapper_download() {
  if [[ -x "$MVN_WRAPPER_BIN" ]]; then
    return 0
  fi
  echo "[..] 首次需下载 Maven 3.9.6（约 9MB），网络慢时会较久..." >&2
  echo "     本机已有 mvn 时请确认在 WSL 内运行本脚本（不要用无 mvn 的 Git Bash）" >&2
  if [[ -x "$ROOT/mvnw" ]]; then
    "$ROOT/mvnw" -version >/dev/null
  else
    echo "mvnw not found" >&2
    exit 1
  fi
}

resolve_mvn() {
  # 默认本机 mvn；仅 USE_MVNW=1 时强制 ./mvnw 下载
  if [[ "${USE_MVNW:-0}" == "1" ]]; then
    ensure_maven_wrapper_download
    MVN_EXEC="$MVN_WRAPPER_BIN"
    echo "./mvnw (USE_MVNW=1)"
    return
  fi
  if command -v mvn >/dev/null 2>&1 && mvn -version >/dev/null 2>&1; then
    MVN_EXEC="$(command -v mvn)"
    echo "mvn (system): $MVN_EXEC — $(mvn -version 2>&1 | head -1)"
    return
  fi
  ensure_maven_wrapper_download
  MVN_EXEC="$MVN_WRAPPER_BIN"
  echo "./mvnw (fallback)"
}

run_mvn() {
  [[ -n "$MVN_EXEC" ]] || resolve_mvn >/dev/null
  (cd "$ROOT" && "$MVN_EXEC" "$@")
}

mvn_display() {
  [[ -n "$MVN_EXEC" ]] && echo "$MVN_EXEC" && return
  resolve_mvn
}

tcp_open() {
  local host="$1" port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 3 "$host" "$port" >/dev/null 2>&1
    return $?
  fi
  (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1
}

ensure_java_maven() {
  command -v java >/dev/null 2>&1 || { echo "java not found (need JDK 17+)" >&2; exit 1; }
  echo "[OK] $(java -version 2>&1 | head -1)"
  echo "[OK] Maven: $(resolve_mvn)"
}

start_local_deps() {
  command -v docker >/dev/null 2>&1 || { echo "[WARN] docker not found" >&2; return 1; }
  [[ -f "$ROOT/docker-compose.yml" ]] || { echo "[WARN] docker-compose.yml missing" >&2; return 1; }

  export DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-root@dev}"
  export DB_NAME="${DB_NAME:-intelligence}"
  export DB_USERNAME="${DB_USERNAME:-intelligence}"
  export DB_PASSWORD="${DB_PASSWORD:-intelligence@2026}"

  echo "[..] docker compose up mysql redis ..."
  docker compose -f "$ROOT/docker-compose.yml" up -d mysql redis
  DB_HOST="127.0.0.1"
  REDIS_HOST="127.0.0.1"
  export DB_HOST REDIS_HOST
  echo "[OK] local MySQL :3306, Redis :6379"
  echo "     Set DB_HOST=127.0.0.1 REDIS_HOST=127.0.0.1 if yml still points remote"
  sleep 5
}

check_deps() {
  echo "[..] MySQL ${DB_HOST}:${DB_PORT} ..."
  if tcp_open "$DB_HOST" "$DB_PORT"; then
    echo "[OK] MySQL port open"
    mysql_ok=true
  else
    echo "[FAIL] MySQL port closed" >&2
    mysql_ok=false
  fi

  echo "[..] Redis ${REDIS_HOST}:${REDIS_PORT} ..."
  if tcp_open "$REDIS_HOST" "$REDIS_PORT"; then
    echo "[OK] Redis port open"
  else
    echo "[WARN] Redis port closed (server uses in-memory cache fallback)"
  fi

  $mysql_ok
}

do_compile() {
  local modules="$1"
  echo "[..] install -pl $modules -am (install 到本地仓库供 spring-boot:run) ..."
  run_mvn -pl "$modules" -am install -DskipTests -q
  echo "[OK] install done"
}

compile_modules_for() {
  case "$1" in
    server) echo "meeting-config-core,meeting-server" ;;
    admin)  echo "meeting-config-core,meeting-admin-server" ;;
    both)   echo "meeting-config-core,meeting-server,meeting-admin-server" ;;
    compile) echo "meeting-config-core,meeting-server,meeting-admin-server" ;;
    *) echo "" ;;
  esac
}

# 在模块目录执行 spring-boot:run（避免 -pl -am 在父 pom 上跑导致无 mainClass）
spawn_module() {
  local module="$1"
  local port="$2"
  local title="$3"
  local mod_dir="${ROOT}/${module}"
  local log="$ROOT/logs/${module}.log"
  local pidfile="$ROOT/logs/${module}.pid"
  local run_line
  [[ -n "$MVN_EXEC" ]] || resolve_mvn >/dev/null
  [[ -d "$mod_dir" ]] || { echo "module dir not found: $mod_dir" >&2; exit 1; }

  run_line="cd \"${mod_dir}\" && export SPRING_PROFILES_ACTIVE=\"${SPRING_PROFILES_ACTIVE}\" INTERNAL_RELOAD_TOKEN=\"${INTERNAL_RELOAD_TOKEN}\" ADMIN_TOKEN=\"${ADMIN_TOKEN}\" MEETING_SERVER_URL=\"${MEETING_SERVER_URL}\" && echo \">>> ${title} :${port} profile=${SPRING_PROFILES_ACTIVE}\" && \"${MVN_EXEC}\" spring-boot:run"

  if [[ "$(uname -s)" == MINGW* ]] || [[ "$(uname -s)" == MSYS* ]] || [[ -n "${WINDIR:-}" ]]; then
    if command -v start >/dev/null 2>&1; then
      start "$title" bash -lc "$run_line"
      echo "[OK] started $title in new window -> http://127.0.0.1:$port"
      return 0
    fi
    if command -v cmd.exe >/dev/null 2>&1; then
      cmd.exe //c start "" bash -lc "$run_line"
      echo "[OK] started $title in new window -> http://127.0.0.1:$port"
      return 0
    fi
  fi
  if command -v gnome-terminal >/dev/null 2>&1; then
    gnome-terminal --title="$title" -- bash -lc "$run_line; exec bash"
    echo "[OK] started $title -> http://127.0.0.1:$port"
    return 0
  fi
  if command -v osascript >/dev/null 2>&1; then
    osascript -e "tell app \"Terminal\" to do script \"$run_line\""
    echo "[OK] started $title -> http://127.0.0.1:$port"
    return 0
  fi

  mkdir -p "$ROOT/logs"
  echo "[..] no GUI terminal; background $module (log: $log)"
  : >"$log"
  nohup bash -lc "$run_line" >>"$log" 2>&1 &
  echo $! >"$pidfile"
  wait_for_service "$port" "$log" "$pidfile" "$title"
}

wait_for_service() {
  local port="$1" log="$2" pidfile="$3" name="$4"
  echo "[..] waiting for $name on :$port (up to 90s) ..."
  local i
  for i in $(seq 1 45); do
    if tcp_open 127.0.0.1 "$port"; then
      echo "[OK] $name listening http://127.0.0.1:$port"
      return 0
    fi
    if [[ -f "$pidfile" ]] && ! kill -0 "$(cat "$pidfile")" 2>/dev/null; then
      echo "[FAIL] $name process exited. Log tail:" >&2
      tail -30 "$log" >&2
      return 1
    fi
    sleep 2
  done
  echo "[WARN] :$port not ready; tail -f $log" >&2
  tail -15 "$log" >&2
  return 1
}

print_menu_options() {
  # 必须输出到 stderr，否则会被 choice="$(resolve_target)" 捕获
  {
    echo ""
    echo "./scripts/start-dev.sh              # 交互菜单"
    echo "./scripts/start-dev.sh server       # meeting-server :8765"
    echo "./scripts/start-dev.sh admin        # meeting-admin :8766"
    echo "./scripts/start-dev.sh both         # 两个都起"
    echo "./scripts/start-dev.sh both --local-deps   # 先 docker 起 MySQL+Redis"
    echo "./scripts/start-dev.sh deps         # 只起依赖"
    echo "./scripts/start-dev.sh compile      # 只编译"
    echo ""
    echo "Choice: 1-7 对应上表顺序，或 server/admin/both/deps/compile，0 退出"
    echo "提示: WSL 下已装 mvn 时会自动用本机 Maven；勿在 Git Bash 无 mvn 环境跑"
    echo ""
  } >&2
}

show_menu() {
  local c
  while true; do
    print_menu_options
    read -r -p "Choice [0-7]: " c
    c="$(echo "$c" | tr '[:upper:]' '[:lower:]' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    if [[ -z "$c" ]]; then
      echo "提示: 请输入 1-7 或 server/admin/both/deps/compile，0 退出" >&2
      echo "" >&2
      continue
    fi
    case "$c" in
      1|menu)
        continue
        ;;
      2|server|s)
        echo server
        return 0
        ;;
      3|admin|a)
        echo admin
        return 0
        ;;
      4|both|b)
        echo both
        return 0
        ;;
      5|both-local|both-deps|bl|'both --local-deps')
        WITH_LOCAL_DEPS=true
        echo both
        return 0
        ;;
      6|deps|d)
        echo deps
        return 0
        ;;
      7|compile|c)
        echo compile
        return 0
        ;;
      0|exit|quit|q)
        echo exit
        return 0
        ;;
      *)
        echo "无效选项: \"$c\"（请参考上方 1-7）" >&2
        echo ""
        ;;
    esac
  done
}

resolve_target() {
  if [[ "$TARGET" != "menu" ]]; then
    echo "$TARGET"
  else
    show_menu
  fi
}

run_target() {
  local choice="$1"
  [[ "$TARGET" != menu ]] && banner
  ensure_java_maven

  if [[ "$WITH_LOCAL_DEPS" == true ]] || [[ "$choice" == deps ]]; then
    start_local_deps || true
    if [[ "$choice" == deps ]]; then
      [[ "$SKIP_DEP_CHECK" == true ]] || check_deps || true
      echo "Deps ready. Run: ./scripts/start-dev.sh server|admin|both"
      exit 0
    fi
  fi

  if [[ "$SKIP_DEP_CHECK" != true ]]; then
    if ! check_deps; then
      if [[ "$WITH_LOCAL_DEPS" != true ]]; then
        echo ""
        echo "MySQL unreachable. Try: ./scripts/start-dev.sh both --local-deps"
        read -r -p "Continue anyway? (y/N) " cont
        [[ "$cont" =~ ^[yY]$ ]] || exit 1
      fi
    fi
  fi

  local mods
  mods="$(compile_modules_for "$choice")"
  if [[ -n "$mods" ]] && [[ "$SKIP_COMPILE" != true ]]; then
    do_compile "$mods"
  fi

  case "$choice" in
    server)
      spawn_module meeting-server 8765 meeting-server
      echo "Host: http://127.0.0.1:8765/host/{meetingId}"
      ;;
    admin)
      spawn_module meeting-admin-server 8766 meeting-admin-server
      echo "Admin: http://127.0.0.1:8766/admin  X-Admin-Token: $ADMIN_TOKEN"
      ;;
    both)
      spawn_module meeting-server 8765 meeting-server
      sleep 2
      spawn_module meeting-admin-server 8766 meeting-admin-server
      echo ""
      echo "Both started. Wait for :8765 before admin internal APIs."
      echo "  Admin: http://127.0.0.1:8766/admin  token=$ADMIN_TOKEN"
      echo "  Server: http://127.0.0.1:8765"
      ;;
    compile)
      ;;
    *)
      echo "Unknown target: $choice" >&2
      exit 1
      ;;
  esac

  echo ""
  echo "Close terminal window (or kill pid in logs/*.pid) to stop."
}

[[ "$TARGET" == menu ]] && banner
choice="$(resolve_target | tail -1)"
choice="$(echo "$choice" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
[[ "$choice" == exit ]] && exit 0
[[ -z "$choice" ]] && { echo "未选择启动项" >&2; exit 1; }
run_target "$choice"
