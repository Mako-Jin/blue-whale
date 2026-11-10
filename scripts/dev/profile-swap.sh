#!/usr/bin/env bash
# ============================================================
# 扩展 profile 换位：在 8G Docker VM 内腾出内存后起 ops/collab/aiot/biz 扩展组
#
# 背景：20 个核心服务已占 VM 约 7.2G，扩展组单组需 1.0~2.7G，物理上放不下。
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
# mem_limit 是上限，不是实际占用：实测稳态约为上限的 38%~43%
#（bw-crm 340/900、bw-oa 343/900、bw-auth 371/900、bw-job 392/900；480 上限的约 200/480）
# 取 45% 作预算（含 3~7 个百分点余量）。按 60% 算会把「20 核心 + 一组扩展」判成永远放不下。
RATIO_PCT=${RATIO_PCT:-45}
# 已运行服务的稳态占上限比例（用于算「预热还会再涨多少」）
WARM_PCT=${WARM_PCT:-45}
# 启动不足这么多秒的容器，其 MemAvailable 读数不可信
WARMUP_SEC=${WARMUP_SEC:-240}
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
  # Windows 上的 python 输出 CRLF，被 bash 读进 $(...) 后数字会带 \r 导致算术判断报
  # "invalid arithmetic operator"，出口统一削掉
  if command -v python3 >/dev/null 2>&1; then python3 "$@"; else python "$@"; fi | tr -d '\r'
}

docker_up() { docker info >/dev/null 2>&1; }

# Nacos 是硬前置：缺它所有 Spring 服务会卡在「注册失败 → 退出 → restart 重试」的死循环，
# 表现是 docker ps 里 Up N seconds 一路递增、内存被反复分配的启动堆吃满（实测把 7G 余量吃到 711MB）。
nacos_up() { (exec 3<>/dev/tcp/127.0.0.1/8848) 2>/dev/null && exec 3>&- && return 0 || return 1; }

# 目标 profile 的镜像是否都已构建（编排里没有 build 段，缺镜像 up -d 会直接报错）
missing_images() {
  compose --profile "$1" config --format json 2>/dev/null | py -c '
import json,sys,subprocess
d=json.load(sys.stdin)
have={l.split()[0] for l in subprocess.run(["docker","images","--format","{{.Repository}}:{{.Tag}}"],
     capture_output=True,text=True).stdout.splitlines() if l.strip()}
miss=[]
for n,s in d.get("services",{}).items():
    img=s.get("image")
    if img and img not in have:
        miss.append(n+"("+img+")")
print(" ".join(miss))
' "$1"
}

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

# 已运行服务的「还会再涨多少」：刚起的 JVM RSS 只有稳态的 1/3，此时 MemAvailable 虚高，
# 直接用会把「看起来够」判成真的够，等预热完就 OOM。按 WARM_PCT 的稳态估量补差额。
growth_reserve_mb() {
  local limits
  # 用 compose 里的 mem_limit（容器名 = bw-<service>）；stats 报的「上限」对无 mem_limit 的
  # 中间件是 VM 总量，直接拿来算会把 redis 当成能涨 3.5G，必须按服务表取
  limits="$(service_limits | awk -F'\t' '{printf "bw-%s=%s ", $1, $2}')"
  docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}' 2>/dev/null | py -c '
import sys
WARM=float(sys.argv[1])
lim={}
for kv in sys.argv[2].split():
    if "=" in kv:
        k,v=kv.split("=",1)
        lim[k]=float(v)
def mb(x):
    for u,f in (("GiB",1024.0),("MiB",1.0),("KiB",1/1024.0)):
        if x.endswith(u):
            try: return float(x[:-len(u)])*f
            except ValueError: return 0.0
    return 0.0
tot=0.0
for line in sys.stdin:
    line=line.strip()
    if not line or "\t" not in line: continue
    name,usage=line.split("\t",1)
    limit=lim.get(name)
    if not limit: continue
    parts=[p.strip() for p in usage.split("/")]
    if len(parts)!=2: continue
    tot+=max(0.0, limit*WARM/100.0-mb(parts[0]))
print(int(tot))
' "$WARM_PCT" "$limits"
}

# 启动时间不足 WARMUP_SEC 的容器数（读数不可信）
warming_count() {
  local n=0 age
  while read -r c; do
    age="$(docker inspect "$c" --format '{{.State.StartedAt}}' 2>/dev/null || true)"
    [ -z "$age" ] && continue
    local s
    s="$(date -d "$age" +%s 2>/dev/null || true)"
    [ -z "$s" ] && continue
    [ $(( $(date +%s) - s )) -lt "${WARMUP_SEC}" ] && n=$((n+1))
  done <<< "$(docker ps --format '{{.Names}}' 2>/dev/null)"
  echo "$n"
}

# 本次 up 会带起来的服务：默认组（无 profiles）+ 目标 profile 自有服务
up_targets() {
  compose --profile "$1" config --format json 2>/dev/null | py -c '
import json,sys
d=json.load(sys.stdin)
prof=sys.argv[1]
for n,s in sorted(d.get("services",{}).items()):
    if (not s.get("profiles")) or prof in s["profiles"]:
        print(n)
' "$1"
}

# 目标 profile「自有服务」的 mem_limit 合计（默认组默认起，不计入扩展组预算）
profile_delta_mb() {
  local prof="$1"
  compose --profile "$prof" config --format json 2>/dev/null | py -c '
import json,sys
d=json.load(sys.stdin)
prof=sys.argv[1]
running=set(sys.argv[2].split())
tot=0
for n,s in d.get("services",{}).items():
    if prof not in (s.get("profiles") or []):
        continue
    if n in running:
        continue
    m=s.get("mem_limit",0)
    if isinstance(m,str):
        m=int(m.rstrip("mM")) if m[:-1].isdigit() else int(m)
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
  local growth eff
  growth="$(growth_reserve_mb)"; eff=$((avail - growth))
  echo "== Docker VM =="
  info "总量 ${total}MB / 可用 ${avail}MB / 红线 ${RESERVE_MB}MB"
  info "已运行服务预热后还会再涨约 ${growth}MB → 有效余量 ${eff}MB"
  echo "== 依赖前置 =="
  info "Nacos 8848：$(nacos_up && echo 'UP' || echo 'DOWN ← 所有服务会注册失败死循环')"
  info "RabbitMQ/Redis：$(docker ps --format '{{.Names}}' | grep -cE 'bw-(redis|rabbitmq)' ) / 2 容器在线"
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
nacos_up || die "Nacos 8848 未就绪。缺它所有 Spring 服务会「注册失败→退出→重启」死循环，起多少都白起。
  先执行：powershell -ExecutionPolicy Bypass -File scripts/dev/start-nacos.ps1"
MISSING_IMG="$(missing_images "$PROFILE")"
[ -z "$MISSING_IMG" ] || die "缺镜像：$MISSING_IMG
  先执行：scripts/dev/build-service-images.sh ${MISSING_IMG%%(*}"

# —— 显式计算本次要启动的集合 ——
# 不能只算 profile 自有服务：核心组若被停过，--profile up -d 会把 20 个一起拉起来，
# 预算若只算扩展组就会严重低估（实测核心 20 个稳态约 6.6G，扩展组才 1.2G）。
declare -A LIMIT=()
while IFS=$'\t' read -r n m; do [ -n "$n" ] && LIMIT[$n]="$m"; done <<< "$(service_limits)"

sum_limits() {
  local tot=0 n
  for n in $1; do tot=$((tot + ${LIMIT[$n]:-0})); done
  echo "$tot"
}

ALL_TARGETS="$(up_targets "$PROFILE")"
RUNNING="$(running_services)"
TO_START="$(printf '%s\n' "$ALL_TARGETS" | grep -vxF -f <(printf '%s\n' "$RUNNING") || true)"

AVAIL_MB="$(vm_avail_mb)"
GROWTH_MB="$(growth_reserve_mb)"
# 预热补偿：刚起的服务 RSS 只有稳态的一半左右，直接用 MemAvailable 会把「虚高余量」当真实余量
EFF_AVAIL_MB=$((AVAIL_MB - GROWTH_MB))
WARMING="$(warming_count)"

echo "== 计划 =="
info "目标 profile：$PROFILE"
info "待启动 $(printf '%s\n' "$TO_START" | grep -c .) 个（默认组 + ${PROFILE} 自有，已运行的跳过）"
info "VM 可用 ${AVAIL_MB}MB；预热还会再涨约 ${GROWTH_MB}MB → 有效余量 ${EFF_AVAIL_MB}MB，红线 ${RESERVE_MB}MB"
if [ "${WARMING:-0}" -gt 0 ]; then
  info "注意：${WARMING} 个容器启动不足 ${WARMUP_SEC}s，读数可能偏低"
fi

DROPPED=()
while :; do
  NEED_MB=$(( $(sum_limits "$TO_START") * RATIO_PCT / 100 ))
  [ "$((EFF_AVAIL_MB - RESERVE_MB))" -ge "$NEED_MB" ] && break
  [ "${#DROPPED[@]}" -ge "$MAX_STOP" ] && break
  pick=""
  for entry in "${STOP_CANDIDATES[@]}"; do
    cand="${entry%%|*}"
    if printf '%s\n' "$TO_START" | grep -qx "$cand"; then pick="$cand"; pick_reason="${entry#*|}"; break; fi
  done
  [ -z "$pick" ] && break
  TO_START="$(printf '%s\n' "$TO_START" | grep -vxF "$pick")"
  DROPPED+=("$pick")
  info "→ 让出 $pick（$(mem_of "$pick")MB）：$pick_reason"
done

NEED_MB=$(( $(sum_limits "$TO_START") * RATIO_PCT / 100 ))
if [ "$((EFF_AVAIL_MB - RESERVE_MB))" -lt "$NEED_MB" ]; then
  echo "余量仍不足：有效余量 ${EFF_AVAIL_MB}MB - 红线 ${RESERVE_MB}MB < 实需 ${NEED_MB}MB" >&2
  echo "调大 --max-stop，或改起更小的组（ops ≈1.0G / biz ≈1.1G）" >&2
  exit 2
fi
info "待启动上限 $(sum_limits "$TO_START")MB，按 ${RATIO_PCT}% 估算实需 ${NEED_MB}MB"
[ "${#DROPPED[@]}" -eq 0 ] && info "余量已够，无需让出核心服务"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "--dry-run：不做任何变更"
  exit 0
fi
if [ "$AUTO_YES" -ne 1 ]; then
  printf '继续？让出 [%s] 并启动上述服务 [y/N] ' "${DROPPED[*]:-无}"
  read -r ans || ans=""
  case "$ans" in y|Y|yes|YES) ;; *) echo "已取消"; exit 0 ;; esac
fi

mkdir -p "$(dirname "$STATE")"
if [ "${#DROPPED[@]}" -gt 0 ]; then
  RUN_DROP=""
  for d in "${DROPPED[@]}"; do
    printf '%s\n' "$RUNNING" | grep -qx "$d" && RUN_DROP="$RUN_DROP $d"
  done
  # 没在跑的只是不启动，在跑的才需要真停
  # shellcheck disable=SC2086
  [ -n "${RUN_DROP// /}" ] && compose stop $RUN_DROP
  printf 'stopped=%s\nprofile=%s\ntime=%s\n' "${DROPPED[*]}" "$PROFILE" "$(date '+%F %T')" > "$STATE"
fi

if [ -z "${TO_START//[[:space:]]/}" ]; then
  info "目标服务都已在运行，不需要 up"
else
  # shellcheck disable=SC2086
  compose up -d $TO_START
fi

echo "== 起服后 =="
sleep 5
info "VM 可用：$(vm_avail_mb)MB（红线 ${RESERVE_MB}MB）"
docker ps --filter 'label=com.docker.compose.project=bluewhale-app' \
  --format '{{.Names}}\t{{.Status}}' | sort | tail -20
[ "${#DROPPED[@]}" -gt 0 ] && echo "回滚：scripts/dev/profile-swap.sh --restore（会拉回 ${DROPPED[*]}）"
exit 0
