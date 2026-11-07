-- ============================================================
-- 蓝鲸系统 - 业务数据库初始化
-- 仅在 MySQL 数据卷为空时执行一次（docker-entrypoint-initdb.d）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---- 权限中心 ----
CREATE DATABASE IF NOT EXISTS bluewhale_auth
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 定时任务 ----
CREATE DATABASE IF NOT EXISTS bluewhale_job
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 工作流 ----
CREATE DATABASE IF NOT EXISTS bluewhale_workflow
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 表单 ----
CREATE DATABASE IF NOT EXISTS bluewhale_form
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 文件 ----
CREATE DATABASE IF NOT EXISTS bluewhale_file
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 监控 ----
CREATE DATABASE IF NOT EXISTS bluewhale_monitor
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 审计 ----
CREATE DATABASE IF NOT EXISTS bluewhale_audit
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 支付 ----
CREATE DATABASE IF NOT EXISTS bluewhale_pay
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 告警 ----
CREATE DATABASE IF NOT EXISTS bluewhale_alert
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 统一消息 ----
CREATE DATABASE IF NOT EXISTS bluewhale_message
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 邮箱服务（Wave 5.89 M0：账号/文件夹/邮件索引/附件/草稿） ----
CREATE DATABASE IF NOT EXISTS bluewhale_mail
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 地图服务（Wave 5.91 M0：业务打点/解析缓存/供应商配额） ----
CREATE DATABASE IF NOT EXISTS bluewhale_map
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- AGENT ----
CREATE DATABASE IF NOT EXISTS bluewhale_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- Nacos 配置中心（外置存储可选） ----
CREATE DATABASE IF NOT EXISTS bluewhale_nacos
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- ERP ----
CREATE DATABASE IF NOT EXISTS bluewhale_erp
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- MES ----
CREATE DATABASE IF NOT EXISTS bluewhale_mes
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- PDM ----
CREATE DATABASE IF NOT EXISTS bluewhale_pdm
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- OA ----
CREATE DATABASE IF NOT EXISTS bluewhale_oa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 音乐平台（Wave 5.75） ----
CREATE DATABASE IF NOT EXISTS bluewhale_music
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 城池场景导航（Wave 5.77 M2，2D 优先） ----
CREATE DATABASE IF NOT EXISTS bluewhale_scene
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 客户关系（Wave 5.77 门禁骨架，门禁通过前仅 /crm/meta） ----
CREATE DATABASE IF NOT EXISTS bluewhale_crm
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 学习培训考试（Wave 5.87 M0 底座 + M1 课程与学习） ----
CREATE DATABASE IF NOT EXISTS bluewhale_lms
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- ---- 商城（Wave 5.94 M0：商品/渠道/购物车/订单） ----
CREATE DATABASE IF NOT EXISTS bluewhale_mall
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
