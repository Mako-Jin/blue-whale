-- =====================================================================
-- whale-message：OA M0 通知模板种子（幂等）
-- =====================================================================
-- 适用库   : bluewhale_message
-- 执行方式 : mysql -u<user> -p bluewhale_message < migration-5.86-oa-m0-notify.sql
-- 幂等说明 : INSERT ... WHERE NOT EXISTS（tenant_id + template_code），
--            可重复执行，不覆盖既有模板。
-- 上级计划 : Wave 5.82 M0-6（模板/流程定义种子）
-- 背景     : OA 审批待办 / 结果 / 抄送 / 催办 / 假期额度 5 类站内通知模板；
--            OA 侧只引用 template_code，占位符由消息服务按 payload 渲染。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 审批待办
-- ---------------------------------------------------------------------
INSERT INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000201, 1, 'OA_APPROVAL_TODO', '审批待办',
       '${applicant}提交的${title}待您审批',
       '申请人：${applicant}\n审批事项：${title}\n当前节点：${nodeName}\n处理入口：${businessUrl}',
       1, 'M0-6 种子：OA 审批待办通知'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM msg_template WHERE tenant_id = 1 AND template_code = 'OA_APPROVAL_TODO'
);

-- ---------------------------------------------------------------------
-- 2. 审批结果
-- ---------------------------------------------------------------------
INSERT INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000202, 1, 'OA_APPROVAL_RESULT', '审批结果',
       '您的${title}已${result}',
       '审批事项：${title}\n审批结果：${result}\n审批意见：${comment}\n查看详情：${businessUrl}',
       1, 'M0-6 种子：OA 审批结果通知'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM msg_template WHERE tenant_id = 1 AND template_code = 'OA_APPROVAL_RESULT'
);

-- ---------------------------------------------------------------------
-- 3. 审批抄送
-- ---------------------------------------------------------------------
INSERT INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000203, 1, 'OA_APPROVAL_CC', '审批抄送',
       '${applicant}的${title}已抄送给您',
       '申请人：${applicant}\n审批事项：${title}\n查看详情：${businessUrl}',
       1, 'M0-6 种子：OA 审批抄送通知'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM msg_template WHERE tenant_id = 1 AND template_code = 'OA_APPROVAL_CC'
);

-- ---------------------------------------------------------------------
-- 4. 审批催办
-- ---------------------------------------------------------------------
INSERT INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000204, 1, 'OA_APPROVAL_URGE', '审批催办',
       '您有一条待审批被催办：${title}',
       '审批事项：${title}\n催办人：${urger}\n催办说明：${comment}\n处理入口：${businessUrl}',
       1, 'M0-6 种子：OA 审批催办通知'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM msg_template WHERE tenant_id = 1 AND template_code = 'OA_APPROVAL_URGE'
);

-- ---------------------------------------------------------------------
-- 5. 假期额度提醒
-- ---------------------------------------------------------------------
INSERT INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000205, 1, 'OA_LEAVE_BALANCE', '假期额度提醒',
       '假期额度提醒：${leaveType}',
       '假期类型：${leaveType}\n当前额度：${balance}\n变动说明：${action}',
       1, 'M0-6 种子：OA 假期额度提醒'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM msg_template WHERE tenant_id = 1 AND template_code = 'OA_LEAVE_BALANCE'
);
