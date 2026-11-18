-- =====================================================================
-- 修正 Auth 任务的执行器应用名（幂等）
-- =====================================================================
-- 背景：V9 种子按习惯写成 whale-auth，但该服务的
--       spring.application.name = whale-service-auth，执行器注册、
--       Nacos 服务名、job_info.executor_app 三者必须一致，否则调度侧
--       寻址为空并直接记「找不到可用实例」。
-- 处理：不改 V9（已应用，改文件会破坏 Flyway checksum），此处幂等修正。
-- =====================================================================

UPDATE job_info
SET executor_app = 'whale-service-auth'
WHERE job_code = 'auth.password.expiry-notice'
  AND deleted = 0
  AND executor_app <> 'whale-service-auth';
