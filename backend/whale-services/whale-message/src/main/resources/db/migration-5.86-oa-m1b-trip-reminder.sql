-- =====================================================================
-- whale-message：OA M1B-2b 出差报告未交提醒模板种子（幂等）
-- =====================================================================
-- 适用库   : bluewhale_message
-- 执行方式 : mysql -u<user> -p bluewhale_message < migration-5.86-oa-m1b-trip-reminder.sql
-- 幂等说明 : INSERT ... WHERE NOT EXISTS（tenant_id + template_code），
--            可重复执行，不覆盖既有模板。
-- 上级计划 : Wave 5.82 M1B-2 剩余项（出差报告未交提醒）
-- 背景     : 出差审批通过且结束超过宽限期仍未提交报告时，向申请人发送站内信；
--            OA 侧只引用 template_code，占位符由消息服务按 payload 渲染。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 出差报告未交提醒
-- ---------------------------------------------------------------------
INSERT INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000206, 1, 'OA_TRIP_REPORT_REMINDER', '出差报告未交提醒',
       '${destination}出差报告提交提醒',
       '申请人：${applicant}\n出差目的地：${destination}\n出差结束日期：${endDate}\n报告状态：待提交\n提交入口：${businessUrl}',
       1, 'M1B-2b 种子：OA 出差报告未交提醒'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM msg_template WHERE tenant_id = 1 AND template_code = 'OA_TRIP_REPORT_REMINDER'
);
