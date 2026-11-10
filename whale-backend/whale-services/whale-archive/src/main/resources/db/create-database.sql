-- =====================================================================
-- BlueWhale 微服务平台 - 档案服务（whale-archive）建库脚本
-- =====================================================================
-- 执行顺序 : 先执行本脚本建库，再执行 init.sql 建表
-- 字符集    : utf8mb4 / utf8mb4_general_ci
-- 说明      : 与 REQ-ARCH-004 命名规范一致（库名 bluewhale_archive）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS bluewhale_archive
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;
