#!/usr/bin/env bash
# ============================================================
# 全量构建 (Linux/macOS)
# ============================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

TARGET="${1:-all}"
SKIP_TESTS="${SKIP_TESTS:-false}"
DOCKER_BUILD="${DOCKER_BUILD:-false}"

step() { echo; echo "===== $1 ====="; }

if [[ "$TARGET" == "all" || "$TARGET" == "backend" ]]; then
  step "后端 Maven 构建"
  cd "$ROOT/backend"
  mvn clean package $([ "$SKIP_TESTS" == "true" ] && echo -DskipTests)
fi

if [[ "$TARGET" == "all" || "$TARGET" == "frontend" ]]; then
  step "前端 pnpm + turbo 构建"
  cd "$ROOT/frontend"
  pnpm install --frozen-lockfile || pnpm install
  pnpm turbo run build
fi

if [[ "$DOCKER_BUILD" == "true" ]]; then
  step "Docker 镜像构建"
  cd "$ROOT/infrastructure/docker"
  docker compose build
fi

echo; echo "全部完成 ✓"
