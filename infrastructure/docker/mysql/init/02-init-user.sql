# ============================================================
# 蓝鲸系统 - 应用用户授权
# 仅在 MySQL 数据卷为空时执行一次
# ============================================================

-- 注意: 请在 .env 中配置 MYSQL_USER / MYSQL_PASSWORD, 生产环境务必替换

CREATE USER IF NOT EXISTS '${MYSQL_USER:-whale_user}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD:-whale_pwd}';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON whale_*.* TO '${MYSQL_USER:-whale_user}'@'%';

-- Nacos 也使用该用户访问 nacos 库 (生产环境建议独立用户)
GRANT ALL PRIVILEGES ON nacos.* TO '${MYSQL_USER:-whale_user}'@'%';

FLUSH PRIVILEGES;
