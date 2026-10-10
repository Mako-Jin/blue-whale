#!/usr/bin/env bash
#
# 在达梦 DM8 上执行建表脚本
#
# 为什么不用 Flyway：
#   达梦下 Flyway 是关闭的（Flyway 官方没有达梦模块，理由见 docs/decisions/0010）。
#   表结构改由本脚本执行交付的 DDL —— 这也与信创交付的常规做法一致：
#   DDL 作为交付物提交、由 DBA 审核后执行，应用不擅自变更库结构。
#
# 用法：
#   BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh
#   BW_DB_USERNAME=bw BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh 10.0.0.5:5236
#
# 环境变量：
#   BW_DB_USERNAME  连接用户，默认 SYSDBA（建议改成应用专用账号）
#   BW_DB_PASSWORD  连接口令，必填
#
# 前置：达梦客户端 disql 在 PATH 中（一般在 $DM_HOME/bin 下）。本仓不带达梦客户端。
#
# 幂等性说明 —— 这条很重要：
#   脚本**刻意不含 IF NOT EXISTS**，因此重复执行会以"表已存在"失败。
#   这是特性不是缺陷：IF NOT EXISTS 会把"表已存在但结构不对"
#   （比如少了 app_release 列）这种真问题静默吞掉，比建表失败危险得多。
#   需要重建时先手工 DROP TABLE，并想清楚存量数据怎么办。
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL_FILE="$ROOT/server/sts-server/sts-server-log/src/main/resources/db/migration/dameng/V1__create_bw_client_log.sql"

ADDR="${1:-127.0.0.1:5236}"
DB_USER="${BW_DB_USERNAME:-SYSDBA}"
DB_PASSWORD="${BW_DB_PASSWORD:-}"

if [ ! -f "$SQL_FILE" ]; then
  echo "错误：未找到建表脚本 $SQL_FILE" >&2
  exit 1
fi

if [ -z "$DB_PASSWORD" ]; then
  echo "错误：未设置 BW_DB_PASSWORD。" >&2
  echo "示例：BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh" >&2
  exit 1
fi

if ! command -v disql >/dev/null 2>&1; then
  echo "错误：未找到 disql（达梦命令行客户端）。" >&2
  echo "请把达梦安装目录下的 bin 加入 PATH，例如：export PATH=\$DM_HOME/bin:\$PATH" >&2
  echo "若手边只有图形客户端（DM 管理工具），也可以直接打开下面这个文件执行：" >&2
  echo "  $SQL_FILE" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 在 Windows 的 Git Bash / MSYS 下，disql.exe 要的是 Windows 路径。
# 不转换的话它会以"文件不存在"失败，而错误信息不会提到路径风格的问题。
# 只在 MSYS 系环境里转换：Linux/WSL 下的 disql 需要的是原生路径。
# ---------------------------------------------------------------------------
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    if command -v cygpath >/dev/null 2>&1; then
      SQL_FILE="$(cygpath -w "$SQL_FILE")"
    fi
    ;;
esac

echo "目标实例：$ADDR"
echo "连接用户：$DB_USER"
echo "建表脚本：$SQL_FILE"
echo

# 口令经命令行传递，会短暂出现在进程列表中。本地开发可接受；
# 生产环境请改用 disql 的交互模式手工执行，或由 DBA 通过工单系统执行脚本。
# shellcheck disable=SC2086
disql "$DB_USER/$DB_PASSWORD@$ADDR" "\`$SQL_FILE\`"

echo
echo "完成。可用下面这句确认表结构（注意达梦会把未加引号的标识符转成大写）："
echo "  SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'BW_CLIENT_LOG';"
