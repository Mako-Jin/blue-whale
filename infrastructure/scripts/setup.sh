#!/usr/bin/env bash
# ============================================================
# 蓝鲸系统 - 本地环境初始化 (Linux/macOS)
# ============================================================

set -eo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { printf "${GREEN}[OK]   ${NC}%s\n" "$*"; }
warn() { printf "${YELLOW}[WARN] ${NC}%s\n" "$*"; }
err()  { printf "${RED}[ERR]  ${NC}%s\n" "$*"; }
hint() { printf "${CYAN}[HINT] ${NC}%s\n" "$*"; }

have() { command -v "$1" >/dev/null 2>&1; }

echo ""
echo "========================================"
echo "  BlueWhale 本地环境初始化 (Linux/macOS)"
echo "========================================"
echo ""

missing=()

for tool in java mvn node git docker; do
  printf "%-15s: " "$tool"
  if have "$tool"; then
    ok "$($tool --version 2>&1 | head -1)"
  else
    warn "未检测到"
    missing+=("$tool")
  fi
done

printf "%-15s: " "pnpm"
if have pnpm; then
  ok "v$(pnpm -v)"
elif have corepack; then
  warn "未激活 (corepack enable && corepack prepare pnpm@latest --activate)"
  missing+=("pnpm")
else
  warn "未检测到"
  missing+=("pnpm")
fi

printf "%-15s: " "docker compose"
if docker compose version >/dev/null 2>&1; then
  ok "$(docker compose version)"
else
  warn "未检测到 docker compose 插件"
  missing+=("docker compose plugin")
fi

echo ""
if [ ${#missing[@]} -eq 0 ]; then
  ok "所有必需依赖已就绪 ✓"
else
  echo "----------------------------------------"
  warn "需安装: ${missing[*]}"
  echo "----------------------------------------"
  hint "Ubuntu/Debian 参考:"
  echo "  sudo apt install -y openjdk-21-jdk maven docker.io docker-compose-plugin git"
  echo "  curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash - && sudo apt install -y nodejs"
  echo "  corepack enable && corepack prepare pnpm@latest --activate"
  echo ""
  hint "macOS (Homebrew) 参考:"
  echo "  brew install --cask temurin21"
  echo "  brew install maven node docker git"
  echo "  corepack enable && corepack prepare pnpm@latest --activate"
fi

echo ""
echo "后续步骤:"
echo "  1. cp infrastructure/docker/.env.example infrastructure/docker/.env   # 修改敏感信息"
echo "  2. cd infrastructure/docker && docker compose up -d"
echo ""
