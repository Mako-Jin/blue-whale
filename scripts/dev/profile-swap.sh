#!/usr/bin/env bash
# ============================================================
# 扩展 profile 换位：在 8G Docker VM 内腾出内存后起 ops/collab/aiot/biz 扩展组
#
# 背景：20 个核心服务已占 VM 约 7.2G，扩展组单组需 1.0~2.0G，物理上放不下。
#       本脚本按「影响面从小到大」停 1~N 个核心服务，腾出余量后再起目标组，
#       并把停掉的服务记进状态文件，--restore 一条命令恢复。
#
# 用法：
#   scripts/dev/profile-swap.sh --status          # 看余量、各扩展组预算、当前建议
#   scripts/dev/profile-swap.sh ops               # 停够核心服务 → 起 ops 组
#   scripts/dev/profile-swap.sh collab --max-stop 3
#   scripts/dev/profile-swap.sh biz --dry-run     # 只打印计划，不动容器
#   scripts/dev/profile-swap.sh --restore         # 把上次停掉的核心服务拉回来
#
# 退出码：0 成功；1 参数/前置错误；2 余量不足（已停到 --max-stop 仍不够）
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE="$ROOT/deploy/backend/docker-compose.services.yml"
ENVFILE="$ROOT/deploy/backend/.env"
STATE="$ROOT/.workbuddy/tmp/profile-swap.state"

# VM 余量红线：低于此值 build/起服都会把引擎拖死（历史事故：docker ps 全部 500）
RESERVE_MB=${RESERVE_MB:-512}
# mem_limit 是上限，实测常驻约为上限的 55%~65%；起服瞬间按上限算会误判，按比例折算
RATIO_PCT=${RATIO_PCT:-60}
MAX_STOP=2
DRY_RUN=0
MODE_ACTION=""

VALID_PROFILES=(ops collab aiot biz ext)

# 可牺牲的核心服务，按「停用影响面」从小到大排列。
# 判据：入站依赖数（谁调用它）、调度任务数、是否有在途状态。
# 注意：gateway/auth/file/job/crm/erp/message/workflow 永不入列。
STOP_CANDIDATES=(
  "mall|无入站依赖、无调度任务；停用期间 CRM 的 order.push/order.reconcile 两个任务会失败"
  "contract|无入站依赖、无调度任务；停用期间 CRM 的 contract.sync 任务会失败"
  "pay|只被 mall 引用；mall 已停时完全无损"
  "audit|无入站依赖；审计事件走 MQ 排队不丢，仅导出/清理任务暂停"
  "video|无入站依赖；2 个转码类调度任务暂停，恢复后按 misfire 补跑"
  "lms|无入站依赖；1 个调度任务暂停"
  "pdm|无入站依赖；1 个调度任务暂停"
  "archive|无入站依赖；2 个归档调度任务暂停"
  "doc|只被 agent 引用（仅 aiot 组需要它，此时不会停）"
  "form|只被 archive 引用"
)

die() { echo "错误：$*" >&2; exit 1; }
info() { echo "  $*"; }

compose() { docker compose -f "$COMPOSE" --env-file "$ENVFILE" "$@"; }

py() {
  if command -v python3 >/dev/null 2>&1; then python3 "$@"; else python "$@"; fi
}

docker_up() { docker info >/dev/null 2>&1; }

vm_avail_mb() {
  local c kb
  c="$(docker ps --format '{{.Names}}' 2>/dev/null | head -1 || true)"
  [ -z "$c" ] && { echo 0; return; }
  kb="$(docker exec "$c" sh -c 'grep MemAvailable /proc/meminfo' 2>/dev/null | tr -dc '0-9' || true)"
  [ -z "$kb" ] && { echo 0; return; }
  echo $((kb / 1024))
}

vm_total_mb() {
  docker info --format '{{.MemTotal}}' 2>/dev/null | awk '{printf "%d", $1/1048576}'
}

# 输出 "name<TAB>mem_mb" —— 从 compose 解析，不依赖守护进程
service_limits() {
  compose --profile ext config --format json 2>/dev/null | py -c '
import json,sys
d=json.load(sys.stdin)
for n,s in sorted(d.get("services",{}).items()):
    m=s.get("mem_limit",0)
    if isinstance(m,str):
        m=int(m.rstrip("mM"))  if m[:-1].isdigit() else int(m)
    print(f"{n}\t{m//1048576}")
'
}

running_services() {
  docker ps --format '{{.Label "com.docker.compose.service"}}' 2>/dev/null | grep -v '^$' | sort -u
}

# 目标 profile 新增服务的 mem_limit 合计（已运行的不重复计）
profile_delta_mb() {
  local prof="$1"
  compose --profile "$prof" config --format json 2>/dev/null | py -c '
import json,sys,subprocess
d=json.load(sys.stdin)
prof=sys.argv[1]
running=set(sys.argv[2].split())
tot=0
for n,s in d.get("services",{}).items():
    m=s.get("mem_limit",0)
    if isinstance(m,str):
        m=int(m.rstrip("mM")) if m[:-1].isdigit() else int(m)
    if n in running:
        continue
    tot+=m//1048576
print(tot)
' "$prof" "$(running_services | tr '\n' ' ')"
}

mem_of() {
  local want="$1" name m
  while IFS=$'\t' read -r name m; do
    [ "$name" = "$want" ] && { echo "$m"; return; }
  done <<< "$(service_limits)"
  echo 0
}

show_status() {
  docker_up || die "Docker 引擎未运行（先启动 Docker Desktop）"
  local total avail
  total="$(vm_total_mb)"; avail="$(vm_avail_mb)"
  echo "== Docker VM =="
  info "总量 ${total}MB / 可用 ${avail}MB / 红线 ${RESERVE_MB}MB"
  echo "== 运行中的服务 =="
  running_services | tr '\n' ' '; echo
  echo "== 扩展组预算（需新增内存，上限口径） =="
  local p need
  for p in "${VALID_PROFILES[@]}"; do
    need="$(profile_delta_mb "$p")"
    printf '  %-7s 新增上限 %5sMB  ≈ 实际 %4sMB  %s\n' "$p" "$need" "$((need * RATIO_PCT / 100))" \
      "$([ "$((need * RATIO_PCT / 100))" -le "$((avail - RESERVE_MB))" ] && echo '可直接起' || echo '需先停核心服务')"
  done
  echo "== 可牺牲核心服务（按影响面排序，前面的先停） =="
  local entry n r
  for entry in "${STOP_CANDIDATES[@]}"; do
    n="${entry%%|*}"; r="${entry#*|}"
    printf '  %-9s %5sMB  %s\n' "$n" "$(mem_of "$n")" "$r"
  done
}

restore() {
  docker_up || die "Docker 引擎未运行"
  [ -f "$STATE" ] || { echo "没有换位记录（$STATE 不存在），无需恢复"; return 0; }
  local stopped
  stopped="$(grep '^stopped=' "$STATE" | cut -d= -f2- || true)"
  [ -z "${stopped// /}" ] && { echo "上次没有停任何服务"; rm -f "$STATE"; return 0; }
  echo "恢复：$stopped"
  # shellcheck disable=SC2086
  compose start $stopped
  rm -f "$STATE"
  echo "已恢复。"
}

# ---------- 参数 ----------
while [ "$#" -gt 0 ]; do
  case "$1" in
    --status) MODE_ACTION=status ;;
    --restore) MODE_ACTION=restore ;;
    --dry-run) DRY_RUN=1 ;;
    --max-stop) shift; MAX_STOP="${1:?--max-stop 需要数字}" ;;
    --yes|-y) AUTO_YES=1 ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) die "未知参数：$1" ;;
    *) PROFILE="$1" ;;
  esac
  shift
done

AUTO_YES=${AUTO_YES:-0}

case "${MODE_ACTION:-run}" in
  status) show_status; exit 0 ;;
  restore) restore; exit 0 ;;
esac

PROFILE="${PROFILE:-}"
[ -n "$PROFILE" ] || die "缺少 profile（ops|collab|aiot|biz|ext），或用 --status / --restore"
printf '%s\n' "${VALID_PROFILES[@]}" | grep -qx "$PROFILE" || die "未知 profile：$PROFILE"

docker_up || die "Docker 引擎未运行（先启动 Docker Desktop，再执行本脚本）"

NEED_LIMIT_MB="$(profile_delta_mb "$PROFILE")"
NEED_MB=$((NEED_LIMIT_MB * RATIO_PCT / 100))
AVAIL_MB="$(vm_avail_mb)"

echo "== 计划 =="
info "目标 profile：$PROFILE（新增服务上限 ${NEED_LIMIT_MB}MB，按 ${RATIO_PCT}% 估算实需 ${NEED_MB}MB）"
info "当前 VM 可用：${AVAIL_MB}MB，预留红线 ${RESERVE_MB}MB"

TO_STOP=()
STOPPED_LIMIT=0
while [ "$((AVAIL_MB + STOPPED_LIMIT * RATIO_PCT / 100 - RESERVE_MB))" -lt "$NEED_MB" ]; do
  [ "${#TO_STOP[@]}" -ge "$MAX_STOP" ] && break
  pick=""
  for entry in "${STOP_CANDIDATES[@]}"; do
    cand="${entry%%|*}"
    if ! printf '%s\n' "${TO_STOP[@]:-}" | grep -qx "$cand"; then pick="$cand"; pick_reason="${entry#*|}"; break; fi
  done
  [ -z "$pick" ] && break
  TO_STOP+=("$pick")
  STOPPED_LIMIT=$((STOPPED_LIMIT + $(mem_of "$pick")))
  info "→ 停 $pick（$(mem_of "$pick")MB）：$pick_reason"
done

if [ "$((AVAIL_MB + STOPPED_LIMIT * RATIO_PCT / 100 - RESERVE_MB))" -lt "$NEED_MB" ]; then
  echo "余量仍不足：可用 ${AVAIL_MB}MB + 停用释放约 $((STOPPED_LIMIT * RATIO_PCT / 100))MB - 红线 ${RESERVE_MB}MB < 需 ${NEED_MB}MB" >&2
  echo "调大 --max-stop，或改起更小的组（ops ≈1.0G / biz ≈1.1G）" >&2
  exit 2
fi

[ "${#TO_STOP[@]}" -eq 0 ] && info "余量已够，无需停核心服务"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "--dry-run：不做任何变更"
  exit 0
fi
if [ "$AUTO_YES" -ne 1 ]; then
  printf '继续？停 [%s] 并起 profile %s [y/N] ' "${TO_STOP[*]:-无}" "$PROFILE"
  read -r ans || ans=""
  case "$ans" in y|Y|yes|YES) ;; *) echo "已取消"; exit 0 ;; esac
fi

mkdir -p "$(dirname "$STATE")"
if [ "${#TO_STOP[@]}" -gt 0 ]; then
  compose stop "${TO_STOP[@]}"
  printf 'stopped=%s\nprofile=%s\ntime=%s\n' "${TO_STOP[*]}" "$PROFILE" "$(date '+%F %T')" > "$STATE"
fi

compose --profile "$PROFILE" up -d

echo "== 起服后 =="
sleep 5
info "VM 可用：$(vm_avail_mb)MB（红线 ${RESERVE_MB}MB）"
docker ps --filter 'label=com.docker.compose.project=bluewhale-app' \
  --format '{{.Names}}\t{{.Status}}' | sort | tail -20
[ "${#TO_STOP[@]}" -gt 0 ] && echo "回滚：scripts/dev/profile-swap.sh --restore（会拉回 ${TO_STOP[*]}）"
exit 0
