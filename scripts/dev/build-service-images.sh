#!/usr/bin/env bash
# ============================================================
# 构建后端服务的运行时镜像（吃已 package 好的 jar，不做 Maven 构建）
#
# 用法：
#   scripts/dev/build-service-images.sh                 # 全部 33 个服务
#   scripts/dev/build-service-images.sh crm oa job      # 只构建指定服务
#   scripts/dev/build-service-images.sh --list          # 列出服务名与 jar 目录
#
# 前置：
#   1) mvn -o -DskipTests -Dcheckstyle.skip=true -Dmaven.test.skip=true package
#   2) Docker 引擎可用，且 VM 余量 > 1G（低于此值 buildkit 会把引擎拖死）：
#        docker exec bw-redis grep MemAvailable /proc/meminfo
#
# 说明：
#   - 每个服务的 build context 只放一个 app.jar（jars/<svc>/app.jar）；
#     放整个 deploy/backend 会让每次构建重复上传数百 MB，buildkit 直接 EOF。
#   - 基础镜像本机只有 wget 没有 curl，镜像内也不做 RUN（buildkit 在 RUN 层偶发 rpc EOF）。
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKERFILE="$ROOT/deploy/backend/Dockerfile.runtime"
JARS_DIR="$ROOT/deploy/backend/jars"

# 服务名:jar 所在模块目录（auth 的产物在 starter 子模块）
SERVICE_JAR_DIRS=(
  "gateway:whale-backend/whale-services/whale-gateway"
  "auth:whale-backend/whale-services/whale-service-auth/whale-service-auth-starter"
  "pay:whale-backend/whale-services/whale-pay"
  "erp:whale-backend/whale-apps/whale-erp"
  "oa:whale-backend/whale-apps/whale-oa"
  "workflow:whale-backend/whale-services/whale-workflow"
  "mes:whale-backend/whale-apps/whale-mes"
  "message:whale-backend/whale-services/whale-message"
  "job:whale-backend/whale-services/whale-job"
  "crm:whale-backend/whale-apps/whale-crm"
  "doc:whale-backend/whale-services/whale-doc"
  "video:whale-backend/whale-services/whale-video"
  "music:whale-backend/whale-services/whale-music"
  "archive:whale-backend/whale-services/whale-archive"
  "audit:whale-backend/whale-ops/whale-audit"
  "lms:whale-backend/whale-apps/whale-lms"
  "party:whale-backend/whale-apps/whale-party"
  "pdm:whale-backend/whale-apps/whale-pdm"
  "form:whale-backend/whale-services/whale-form"
  "file:whale-backend/whale-services/whale-file"
  "contract:whale-backend/whale-apps/whale-contract"
  "mall:whale-backend/whale-apps/whale-mall"
  "pm:whale-backend/whale-apps/whale-pm"
  "alert:whale-backend/whale-ops/whale-alert"
  "monitor:whale-backend/whale-ops/whale-monitor"
  "agent:whale-backend/whale-services/whale-agent"
  "assistant:whale-backend/whale-services/whale-assistant"
  "im:whale-backend/whale-services/whale-im"
  "iot:whale-backend/whale-services/whale-iot"
  "mail:whale-backend/whale-services/whale-mail"
  "map:whale-backend/whale-services/whale-map"
  "scene:whale-backend/whale-services/whale-scene"
  "twin:whale-backend/whale-services/whale-twin"
)

jar_dir_of() {
  local name="$1"
  for entry in "${SERVICE_JAR_DIRS[@]}"; do
    if [ "${entry%%:*}" = "$name" ]; then
      printf '%s' "${entry#*:}"
      return 0
    fi
  done
  return 1
}

if [ "${1:-}" = "--list" ]; then
  for entry in "${SERVICE_JAR_DIRS[@]}"; do
    printf '%-10s %s\n' "${entry%%:*}" "${entry#*:}"
  done
  exit 0
fi

if [ "$#" -gt 0 ]; then
  TARGETS=("$@")
else
  TARGETS=()
  for entry in "${SERVICE_JAR_DIRS[@]}"; do
    TARGETS+=("${entry%%:*}")
  done
fi

# 内存红线：可用内存不足时先停非必需容器，硬构建会让整个引擎失联
if docker info >/dev/null 2>&1; then
  AVAIL_KB="$(docker exec bw-redis grep MemAvailable /proc/meminfo 2>/dev/null | tr -dc '0-9' || true)"
  if [ -n "${AVAIL_KB:-}" ] && [ "$AVAIL_KB" -lt 1048576 ]; then
    echo "Docker VM 可用内存仅 $((AVAIL_KB / 1024))MB（红线 1024MB），先停非必需容器再构建" >&2
    exit 1
  fi
  if [ -n "${AVAIL_KB:-}" ]; then
    echo "Docker VM 可用内存：$((AVAIL_KB / 1024))MB"
  fi
else
  echo "警告：Docker 引擎不可用，仅整理构建上下文，跳过 docker build" >&2
fi

for name in "${TARGETS[@]}"; do
  if ! module_dir="$(jar_dir_of "$name")"; then
    echo "未知服务：$name（用 --list 查看可用服务名）" >&2
    exit 1
  fi
  jar="$(ls "$ROOT/$module_dir"/target/*.jar 2>/dev/null | grep -v 'sources\|javadoc\|original-' | head -1 || true)"
  if [ -z "$jar" ]; then
    echo "缺少 jar：$module_dir/target/*.jar，先执行 mvn package" >&2
    exit 1
  fi
  mkdir -p "$JARS_DIR/$name"
  cp "$jar" "$JARS_DIR/$name/app.jar"
  echo "→ $name  ($(du -h "$JARS_DIR/$name/app.jar" | cut -f1))"
  if docker info >/dev/null 2>&1; then
    docker build -q -f "$DOCKERFILE" -t "bluewhale/whale-$name:dev" "$JARS_DIR/$name" >/dev/null
    echo "  image: bluewhale/whale-$name:dev"
  fi
done

echo "完成：${#TARGETS[@]} 个服务"
