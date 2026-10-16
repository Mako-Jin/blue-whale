#!/usr/bin/env bash
# ============================================================
# 蓝鲸系统 - 开发环境中间件可达性检查 (Linux/macOS)
# 本地手动安装基础设施后, 启动后端服务前检查:
#   MySQL 127.0.0.1:3306 / Redis 127.0.0.1:6379 / Nacos 127.0.0.1:8848
# 用法: ./check-env.sh [目标主机, 默认 127.0.0.1]
# ============================================================

TARGET_HOST="${1:-127.0.0.1}"

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
pass() { printf "${GREEN}[PASS] ${NC}%s\n" "$*"; }
fail() { printf "${RED}[FAIL] ${NC}%s\n" "$*"; }
hint() { printf "${CYAN}[HINT] ${NC}%s\n" "$*"; }

have() { command -v "$1" >/dev/null 2>&1; }

# TCP 探测, 2 秒超时: 优先 nc, 其次 timeout + /dev/tcp, 最后直接 /dev/tcp
test_port() {
  local port="$1"
  if have nc; then
    nc -z -w 2 "$TARGET_HOST" "$port" >/dev/null 2>&1
  elif have timeout; then
    timeout 2 bash -c "</dev/tcp/$TARGET_HOST/$port" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/$TARGET_HOST/$port") >/dev/null 2>&1
  fi
}

# Nacos HTTP 探测: /nacos 返回 200/302 均视为可达
test_nacos() {
  if have curl; then
    local code
    code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' "http://$TARGET_HOST:8848/nacos" 2>/dev/null)
    [ "$code" = "200" ] || [ "$code" = "302" ]
  elif have wget; then
    wget -q -T 2 -O /dev/null "http://$TARGET_HOST:8848/nacos" >/dev/null 2>&1
  else
    # 无 curl/wget 时退化为 TCP 探测
    test_port 8848
  fi
}

echo ""
echo "========================================"
echo "  BlueWhale 中间件可达性检查"
echo "========================================"
echo ""

failed=""

check() { # 名称 端口 [http]
  local name="$1" port="$2" is_http="$3" ok=1
  if test_port "$port"; then
    if [ "$is_http" = "http" ] && ! test_nacos; then ok=0; fi
  else
    ok=0
  fi
  if [ "$ok" = "1" ]; then
    pass "$name  $TARGET_HOST:$port 可达"
  else
    fail "$name  $TARGET_HOST:$port 不可达"
    failed="$failed $name"
  fi
}

check "MySQL" 3306
check "Redis" 6379
check "Nacos" 8848 http

echo ""
if [ -z "$failed" ]; then
  printf "${GREEN}所有中间件已就绪 ✓${NC}\n"
  exit 0
fi

echo "----------------------------------------"
n=$(echo $failed | wc -w)
echo "$n 项中间件检查未通过, 请按以下方式处理:"
echo "----------------------------------------"
for name in $failed; do
  case "$name" in
    MySQL) hint "MySQL: 请启动 MySQL 服务, 或 docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8" ;;
    Redis) hint "Redis: 请启动 Redis 服务, 或 docker run -d --name redis -p 6379:6379 redis:7" ;;
    Nacos) hint "Nacos: 请启动 Nacos (startup.sh -m standalone), 或 docker run -d --name nacos -p 8848:8848 nacos/nacos-server:v2.3.2" ;;
  esac
done
echo ""
exit 1
