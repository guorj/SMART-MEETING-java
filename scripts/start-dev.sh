#!/usr/bin/env bash
# Smart Meeting dev restarter: meeting-server (:8765) / meeting-admin-server (:8766)
# Usage:
#   ./scripts/start-dev.sh              # interactive menu (smart compile: skip if unchanged)
#   ./scripts/start-dev.sh server       # restart meeting-server
#   ./scripts/start-dev.sh admin        # restart meeting-admin
#   ./scripts/start-dev.sh both         # restart both
#   ./scripts/start-dev.sh both --local-deps
#   ./scripts/start-dev.sh deps         # restart docker mysql+redis
#   ./scripts/start-dev.sh compile      # force compile all modules
#   ./scripts/start-dev.sh server --skip-compile    # never compile
#   ./scripts/start-dev.sh server --force-compile   # always compile
#
# Legacy (always compile, no devtools fork tuning): ./scripts/start-dev.legacy.sh
#
# Maven: 默认使用 PATH 中的本机 mvn（WSL 如 /usr/bin/mvn）；仅当无 mvn 或 USE_MVNW=1 时用 ./mvnw
# DevTools: spring-boot:run -Dspring-boot.run.fork=false，改 Java 后 DevTools 热重启（无需反复跑本脚本）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TARGET="menu"
WITH_LOCAL_DEPS=false
SKIP_COMPILE=false
FORCE_COMPILE=false
SKIP_DEP_CHECK=false

STAMP_DIR="$ROOT/.dev/fingerprints"
SCRIPT_START=$(date +%s)
PHASE_START=$SCRIPT_START

usage() {
  sed -n '2,16p' "$0"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-deps) WITH_LOCAL_DEPS=true ;;
    --skip-compile) SKIP_COMPILE=true ;;
    --force-compile) FORCE_COMPILE=true ;;
    --skip-dep-check) SKIP_DEP_CHECK=true ;;
    -h|--help) usage; exit 0 ;;
    server|admin|both|deps|compile|menu) TARGET="$1" ;;
    0) exit 0 ;;
    1) TARGET=server ;;
    2) TARGET=admin ;;
    3) TARGET=both ;;
    4) WITH_LOCAL_DEPS=true; TARGET=both ;;
    5) TARGET=deps ;;
    6) TARGET=compile ;;
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
  echo "  Smart Meeting Dev Restarter (fast)"
  echo "  Root: $ROOT"
  echo "  Legacy: ./scripts/start-dev.legacy.sh"
  echo "========================================"
  echo ""
}

phase_start() {
  PHASE_START=$(date +%s)
}

phase_end() {
  local label="$1"
  local now elapsed
  now=$(date +%s)
  elapsed=$((now - PHASE_START))
  echo "[time] ${label}: ${elapsed}s"
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

  echo "[..] docker compose up + restart mysql redis ..."
  docker compose -f "$ROOT/docker-compose.yml" up -d mysql redis
  docker compose -f "$ROOT/docker-compose.yml" restart mysql redis
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

# 参与 install 的模块（含 -am 传递依赖）
fingerprint_modules_for() {
  case "$1" in
    server)  echo "meeting-config-core,matter-progress-core,meeting-server" ;;
    admin)   echo "meeting-config-core,meeting-admin-server" ;;
    both|compile) echo "meeting-config-core,matter-progress-core,meeting-server,meeting-admin-server" ;;
    *) echo "" ;;
  esac
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

# 模块源码 + pom 指纹（GNU find，WSL/Linux）
module_source_fingerprint() {
  local mod="$1"
  local dir="$ROOT/$mod"
  [[ -d "$dir" ]] || { echo "missing"; return; }
  {
    stat -c '%Y %n' "$ROOT/pom.xml" 2>/dev/null || stat -f '%m %N' "$ROOT/pom.xml" 2>/dev/null || true
    stat -c '%Y %n' "$dir/pom.xml" 2>/dev/null || stat -f '%m %N' "$dir/pom.xml" 2>/dev/null || true
    if command -v find >/dev/null 2>&1; then
      find "$dir/src" -type f 2>/dev/null | sort | while read -r f; do
        stat -c '%Y %n' "$f" 2>/dev/null || stat -f '%m %N' "$f" 2>/dev/null || true
      done
    fi
  } | sha256sum | awk '{print $1}'
}

module_fp_file() {
  echo "$STAMP_DIR/${1}.fp"
}

save_compile_fingerprint() {
  local choice="$1"
  local mods_csv mod
  mods_csv="$(fingerprint_modules_for "$choice")"
  mkdir -p "$STAMP_DIR"
  IFS=',' read -ra MOD_ARR <<< "$mods_csv"
  for mod in "${MOD_ARR[@]}"; do
    module_source_fingerprint "$mod" >"$(module_fp_file "$mod")"
  done
}

modules_need_compile() {
  local choice="$1"
  local mods_csv mod fp stored
  mods_csv="$(fingerprint_modules_for "$choice")"
  IFS=',' read -ra MOD_ARR <<< "$mods_csv"
  for mod in "${MOD_ARR[@]}"; do
    fp="$(module_source_fingerprint "$mod")"
    stored="$(cat "$(module_fp_file "$mod")" 2>/dev/null || true)"
    if [[ -z "$stored" || "$fp" != "$stored" ]]; then
      return 0
    fi
  done
  return 1
}

should_skip_compile() {
  local choice="$1"
  [[ "$SKIP_COMPILE" == true ]] && return 0
  [[ "$FORCE_COMPILE" == true ]] && return 1
  [[ "$choice" == "compile" ]] && return 1
  if modules_need_compile "$choice"; then
    return 1
  fi
  return 0
}

do_compile() {
  local modules="$1"
  phase_start
  echo "[..] install meeting-config-core (供 spring-boot:run 依赖) ..."
  run_mvn -pl meeting-config-core install -DskipTests -q
  echo "[..] install -pl $modules -am (install 到本地仓库供 spring-boot:run) ..."
  run_mvn -pl "$modules" -am install -DskipTests -q
  echo "[OK] install done"
  phase_end "maven install"
}

maybe_compile() {
  local choice="$1"
  local mods
  mods="$(compile_modules_for "$choice")"
  [[ -n "$mods" ]] || return 0

  if should_skip_compile "$choice"; then
    echo "[OK] skip compile (相关模块源码未变；强制: --force-compile)"
    return 0
  fi

  do_compile "$mods"
  save_compile_fingerprint "$choice"
}

kill_pids() {
  local pids="$1"
  [[ -n "$pids" ]] || return 0
  local pid
  for pid in $pids; do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    kill "$pid" 2>/dev/null || true
  done
  sleep 1
  for pid in $pids; do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
  done
}

pids_on_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti ":$port" 2>/dev/null || true
    return 0
  fi
  if command -v fuser >/dev/null 2>&1; then
    fuser "${port}/tcp" 2>/dev/null | tr -s ' ' '\n' | grep -E '^[0-9]+$' || true
    return 0
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -lptn "sport = :$port" 2>/dev/null | sed -n 's/.*pid=\([0-9]*\).*/\1/p' || true
    return 0
  fi
  echo ""
}

stop_module() {
  local module="$1"
  local port="$2"
  local title="$3"
  local pidfile="$ROOT/logs/${module}.pid"
  local stopped=false

  phase_start

  if [[ -f "$pidfile" ]]; then
    local pid
    pid="$(cat "$pidfile" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[..] stopping $title (pid $pid from $pidfile) ..."
      kill_pids "$pid"
      stopped=true
    fi
    rm -f "$pidfile"
  fi

  if tcp_open 127.0.0.1 "$port"; then
    local port_pids
    port_pids="$(pids_on_port "$port")"
    if [[ -n "$port_pids" ]]; then
      echo "[..] stopping $title on :$port (pids: $port_pids) ..."
      kill_pids "$port_pids"
      stopped=true
    fi
  fi

  if command -v pkill >/dev/null 2>&1; then
    if pgrep -f "${ROOT}/${module}.*spring-boot:run" >/dev/null 2>&1; then
      echo "[..] stopping $title (spring-boot:run) ..."
      pkill -f "${ROOT}/${module}.*spring-boot:run" 2>/dev/null || true
      sleep 1
      stopped=true
    fi
  fi

  if [[ "$stopped" == true ]]; then
    local i
    for i in $(seq 1 15); do
      tcp_open 127.0.0.1 "$port" || break
      sleep 1
    done
    if tcp_open 127.0.0.1 "$port"; then
      echo "[WARN] :$port still in use after stop; restart may fail" >&2
    else
      echo "[OK] $title stopped"
    fi
  else
    echo "[..] $title not running on :$port"
  fi

  phase_end "stop $title"
}

# 先停再起：在模块目录执行 spring-boot:run（fork=false 配合 DevTools 热重启）
restart_module() {
  local module="$1"
  local port="$2"
  local title="$3"
  stop_module "$module" "$port" "$title"
  local mod_dir="${ROOT}/${module}"
  local log="$ROOT/logs/${module}.log"
  local pidfile="$ROOT/logs/${module}.pid"
  local run_line
  [[ -n "$MVN_EXEC" ]] || resolve_mvn >/dev/null
  [[ -d "$mod_dir" ]] || { echo "module dir not found: $mod_dir" >&2; exit 1; }

  run_line="cd \"${mod_dir}\" && export SPRING_PROFILES_ACTIVE=\"${SPRING_PROFILES_ACTIVE}\" INTERNAL_RELOAD_TOKEN=\"${INTERNAL_RELOAD_TOKEN}\" ADMIN_TOKEN=\"${ADMIN_TOKEN}\" MEETING_SERVER_URL=\"${MEETING_SERVER_URL}\" && echo \">>> ${title} :${port} profile=${SPRING_PROFILES_ACTIVE} (DevTools fork=false)\" && \"${MVN_EXEC}\" spring-boot:run -Dspring-boot.run.fork=false"

  phase_start

  if [[ "$(uname -s)" == MINGW* ]] || [[ "$(uname -s)" == MSYS* ]] || [[ -n "${WINDIR:-}" ]]; then
    if command -v start >/dev/null 2>&1; then
      start "$title" bash -lc "$run_line"
      echo "[OK] restarted $title in new window -> http://127.0.0.1:$port"
      phase_end "start $title (detached)"
      return 0
    fi
    if command -v cmd.exe >/dev/null 2>&1; then
      cmd.exe //c start "" bash -lc "$run_line"
      echo "[OK] restarted $title in new window -> http://127.0.0.1:$port"
      phase_end "start $title (detached)"
      return 0
    fi
  fi
  if command -v gnome-terminal >/dev/null 2>&1; then
    gnome-terminal --title="$title" -- bash -lc "$run_line; exec bash"
    echo "[OK] restarted $title -> http://127.0.0.1:$port"
    phase_end "start $title (detached)"
    return 0
  fi
  if command -v osascript >/dev/null 2>&1; then
    osascript -e "tell app \"Terminal\" to do script \"$run_line\""
    echo "[OK] restarted $title -> http://127.0.0.1:$port"
    phase_end "start $title (detached)"
    return 0
  fi

  mkdir -p "$ROOT/logs"
  echo "[..] no GUI terminal; background restart $module (log: $log)"
  : >"$log"
  nohup bash -lc "$run_line" >>"$log" 2>&1 &
  echo $! >"$pidfile"
  wait_for_service "$port" "$log" "$pidfile" "$title"
  phase_end "start $title"
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
  {
    echo ""
    echo "  1  server              重启 meeting-server :8765"
    echo "  2  admin               重启 meeting-admin :8766"
    echo "  3  both                重启两个服务"
    echo "  4  both --local-deps   本地 Docker MySQL/Redis + 重启两个服务"
    echo "  5  deps                只重启 docker 依赖"
    echo "  6  compile             强制全量编译"
    echo "  0  exit                退出"
    echo ""
    echo "  也可输入名称: server / admin / both / deps / compile"
    echo "  命令行附加参数: --force-compile | --skip-compile | --skip-dep-check"
    echo "  旧版（每次全量 compile）: ./scripts/start-dev.legacy.sh"
    echo "  提示: 改 Java 后 IDE 保存 → DevTools 热重启；勿反复跑本脚本"
    echo ""
  } >&2
}

show_menu() {
  local c
  while true; do
    print_menu_options
    read -r -p "Choice [0-6]: " c
    c="$(echo "$c" | tr '[:upper:]' '[:lower:]' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    if [[ -z "$c" ]]; then
      echo "提示: 请输入 0-6 或 server/admin/both/deps/compile" >&2
      echo "" >&2
      continue
    fi
    case "$c" in
      1|server|s) echo server; return 0 ;;
      2|admin|a) echo admin; return 0 ;;
      3|both|b) echo both; return 0 ;;
      4|both-local|both-deps|bl|'both --local-deps')
        WITH_LOCAL_DEPS=true
        echo both
        return 0
        ;;
      5|deps|d) echo deps; return 0 ;;
      6|compile|c) echo compile; return 0 ;;
      0|exit|quit|q) echo exit; return 0 ;;
      *)
        echo "无效选项: \"$c\"（请输入 0-6）" >&2
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
  phase_start
  ensure_java_maven
  phase_end "java/maven check"

  if [[ "$WITH_LOCAL_DEPS" == true ]] || [[ "$choice" == deps ]]; then
    phase_start
    start_local_deps || true
    phase_end "local deps"
    if [[ "$choice" == deps ]]; then
      [[ "$SKIP_DEP_CHECK" == true ]] || check_deps || true
      echo "Deps restarted. Run: ./scripts/start-dev.sh server|admin|both"
      exit 0
    fi
  fi

  if [[ "$SKIP_DEP_CHECK" != true ]]; then
    phase_start
    if ! check_deps; then
      if [[ "$WITH_LOCAL_DEPS" != true ]]; then
        echo ""
        echo "MySQL unreachable. Try: ./scripts/start-dev.sh both --local-deps"
        read -r -p "Continue anyway? (y/N) " cont
        [[ "$cont" =~ ^[yY]$ ]] || exit 1
      fi
    fi
    phase_end "dependency check"
  fi

  maybe_compile "$choice"

  case "$choice" in
    server)
      restart_module meeting-server 8765 meeting-server
      echo "Host: http://127.0.0.1:8765/host/{meetingId}"
      ;;
    admin)
      restart_module meeting-admin-server 8766 meeting-admin-server
      echo "Admin: http://127.0.0.1:8766/admin  X-Admin-Token: $ADMIN_TOKEN"
      ;;
    both)
      restart_module meeting-server 8765 meeting-server
      sleep 2
      restart_module meeting-admin-server 8766 meeting-admin-server
      echo ""
      echo "Both restarted. Wait for :8765 before admin internal APIs."
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

  local total=$(( $(date +%s) - SCRIPT_START ))
  echo ""
  echo "[time] total: ${total}s"
  echo "Close terminal window (or kill pid in logs/*.pid) to stop."
  echo "Tip: 改 Java 后 IDE 保存 → DevTools 热重启；改 meeting-config-core 后需 --force-compile"
}

[[ "$TARGET" == menu ]] && banner
choice="$(resolve_target | tail -1)"
choice="$(echo "$choice" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
[[ "$choice" == exit ]] && exit 0
[[ -z "$choice" ]] && { echo "未选择重启项" >&2; exit 1; }
run_target "$choice"
