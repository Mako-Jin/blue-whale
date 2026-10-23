#!/bin/bash
# ============================================================
# 蓝鲸系统 - 应用用户授权
# 仅在 MySQL 数据卷为空时执行一次（docker-entrypoint-initdb.d）
# 应用用户已由入口脚本按 MYSQL_USER / MYSQL_PASSWORD 创建,
# 这里补齐 bluewhale_* 全部业务库的权限。
# 注意: 该文件可能被入口脚本 source 执行（Windows 挂载丢失可执行位），
#       因此不设置 set -e，避免影响入口脚本后续步骤。
# ============================================================

APP_USER="${MYSQL_USER:-whale_user}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON \`bluewhale\\_%\`.* TO '${APP_USER}'@'%';
GRANT ALL PRIVILEGES ON \`bluewhale_nacos\`.* TO '${APP_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL

echo "[init] granted bluewhale_* to ${APP_USER}"
