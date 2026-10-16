# ============================================================
# 蓝鲸系统 - 业务数据库初始化脚本
# 仅在 MySQL 数据卷为空时执行一次
# ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---- 权限中心 ----
CREATE DATABASE IF NOT EXISTS whale_auth
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 定时任务 ----
CREATE DATABASE IF NOT EXISTS whale_job
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 工作流 ----
CREATE DATABASE IF NOT EXISTS whale_workflow
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 表单 ----
CREATE DATABASE IF NOT EXISTS whale_form
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 文件 ----
CREATE DATABASE IF NOT EXISTS whale_file
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 监控 ----
CREATE DATABASE IF NOT EXISTS whale_monitor
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 审计 ----
CREATE DATABASE IF NOT EXISTS whale_audit
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- ERP ----
CREATE DATABASE IF NOT EXISTS whale_erp
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- MES ----
CREATE DATABASE IF NOT EXISTS whale_mes
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- PDM ----
CREATE DATABASE IF NOT EXISTS whale_pdm
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- OA ----
CREATE DATABASE IF NOT EXISTS whale_oa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
