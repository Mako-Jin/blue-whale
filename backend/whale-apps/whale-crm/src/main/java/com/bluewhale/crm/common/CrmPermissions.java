package com.bluewhale.crm.common;

/**
 * CRM 权限码常量（与 {@code migration-5.83-crm.sql} 登记保持一致）。
 *
 * @author BlueWhale
 */
public final class CrmPermissions {

    /** 查看敏感明文（手机 / 邮箱 / 税号 / 敏感备注） */
    public static final String SENSITIVE_VIEW = "sys:crm:sensitive:view";

    /** 客户列表 / 详情 / 360 */
    public static final String CUSTOMER_LIST = "sys:crm:customer:list";

    /** 新建客户 */
    public static final String CUSTOMER_CREATE = "sys:crm:customer:create";

    /** 编辑客户 */
    public static final String CUSTOMER_UPDATE = "sys:crm:customer:update";

    /** 变更客户状态 */
    public static final String CUSTOMER_STATUS = "sys:crm:customer:status";

    /** 变更客户等级 */
    public static final String CUSTOMER_LEVEL = "sys:crm:customer:level";

    /** 客户查重 */
    public static final String CUSTOMER_DUPLICATE = "sys:crm:customer:duplicate";

    /** 逻辑删除客户 */
    public static final String CUSTOMER_DELETE = "sys:crm:customer:delete";

    /** 查询联系人 */
    public static final String CONTACT_LIST = "sys:crm:contact:list";

    /** 管理联系人 */
    public static final String CONTACT_MANAGE = "sys:crm:contact:manage";

    /** 查询地址 */
    public static final String ADDRESS_LIST = "sys:crm:address:list";

    /** 管理地址 */
    public static final String ADDRESS_MANAGE = "sys:crm:address:manage";

    /** 查看画像 */
    public static final String PROFILE_VIEW = "sys:crm:profile:view";

    /** 保存画像 */
    public static final String PROFILE_SAVE = "sys:crm:profile:save";

    /** 查询跟进 */
    public static final String FOLLOW_UP_LIST = "sys:crm:follow-up:list";

    /** 新增跟进 */
    public static final String FOLLOW_UP_CREATE = "sys:crm:follow-up:create";

    /** 查看公海 */
    public static final String POOL_VIEW = "sys:crm:pool:view";

    /** 领取公海 */
    public static final String POOL_CLAIM = "sys:crm:pool:claim";

    /** 释放公海 */
    public static final String POOL_RELEASE = "sys:crm:pool:release";

    /** 分配公海 */
    public static final String POOL_ASSIGN = "sys:crm:pool:assign";

    /** 公海规则 */
    public static final String POOL_RULE = "sys:crm:pool:rule";

    /** 手动回收 */
    public static final String POOL_RECYCLE = "sys:crm:pool:recycle";

    /** 查询线索与活动 */
    public static final String LEAD_LIST = "sys:crm:lead:list";

    /** 新建线索 */
    public static final String LEAD_CREATE = "sys:crm:lead:create";

    /** 编辑线索 */
    public static final String LEAD_UPDATE = "sys:crm:lead:update";

    /** 分配线索负责人 */
    public static final String LEAD_ASSIGN = "sys:crm:lead:assign";

    /** 标记线索无效 */
    public static final String LEAD_INVALID = "sys:crm:lead:invalid";

    /** 线索转化 */
    public static final String LEAD_CONVERT = "sys:crm:lead:convert";

    /** 查询商机、阶段历史与漏斗 */
    public static final String OPPORTUNITY_LIST = "sys:crm:opportunity:list";

    /** 新建商机 */
    public static final String OPPORTUNITY_CREATE = "sys:crm:opportunity:create";

    /** 编辑商机 */
    public static final String OPPORTUNITY_UPDATE = "sys:crm:opportunity:update";

    /** 商机阶段流转 */
    public static final String OPPORTUNITY_STAGE = "sys:crm:opportunity:stage";

    /** 查询报价 */
    public static final String QUOTE_LIST = "sys:crm:quote:list";

    /** 新建报价 */
    public static final String QUOTE_CREATE = "sys:crm:quote:create";

    /** 编辑报价 */
    public static final String QUOTE_UPDATE = "sys:crm:quote:update";

    /** 提交报价审批 */
    public static final String QUOTE_SUBMIT = "sys:crm:quote:submit";

    /** 报价改价新版本 */
    public static final String QUOTE_REVISE = "sys:crm:quote:revise";

    /** 查询销售预测 */
    public static final String FORECAST_LIST = "sys:crm:forecast:list";

    /** 生成与调整销售预测 */
    public static final String FORECAST_MANAGE = "sys:crm:forecast:manage";

    /** 查询销售目标 */
    public static final String TARGET_LIST = "sys:crm:target:list";

    /** 新建与调整销售目标 */
    public static final String TARGET_MANAGE = "sys:crm:target:manage";

    /** 删除销售目标 */
    public static final String TARGET_DELETE = "sys:crm:target:delete";

    /** 查询合同台账与到期提醒 */
    public static final String CONTRACT_LIST = "sys:crm:contract:list";

    /** 同步合同投影 */
    public static final String CONTRACT_SYNC = "sys:crm:contract:sync";

    /** 标记合同续约 */
    public static final String CONTRACT_RENEWAL = "sys:crm:contract:renewal";

    /** 查询回款计划与催收记录 */
    public static final String COLLECTION_LIST = "sys:crm:collection:list";

    /** 维护回款计划分期 */
    public static final String COLLECTION_MANAGE = "sys:crm:collection:manage";

    /** 登记承诺还款与催收记录 */
    public static final String COLLECTION_ACTION = "sys:crm:collection:action";

    /** 查询商务订单 */
    public static final String ORDER_LIST = "sys:crm:order:list";

    /** 查看商务订单详情 */
    public static final String ORDER_DETAIL = "sys:crm:order:detail";

    /** 新建商务订单 */
    public static final String ORDER_CREATE = "sys:crm:order:create";

    /** 编辑商务订单 */
    public static final String ORDER_UPDATE = "sys:crm:order:update";

    /** 提交商务订单审批 */
    public static final String ORDER_SUBMIT = "sys:crm:order:submit";

    /** 重试下发商务订单 */
    public static final String ORDER_RETRY = "sys:crm:order:retry";

    /** 取消商务订单 */
    public static final String ORDER_CANCEL = "sys:crm:order:cancel";

    /** 对账商务订单 */
    public static final String ORDER_RECONCILE = "sys:crm:order:reconcile";

    /** 查询开票申请（列表 / 详情） */
    public static final String INVOICE_LIST = "sys:crm:invoice:list";

    /** 新建 / 编辑开票申请 */
    public static final String INVOICE_CREATE = "sys:crm:invoice:create";

    /** 提交开票审批 */
    public static final String INVOICE_SUBMIT = "sys:crm:invoice:submit";

    /** 登记开票记录 */
    public static final String INVOICE_ISSUE = "sys:crm:invoice:issue";

    /** 登记发票寄送 */
    public static final String INVOICE_SEND = "sys:crm:invoice:send";

    /** 发票红冲 */
    public static final String INVOICE_RED_FLUSH = "sys:crm:invoice:red-flush";

    /** 应收对账查询与同步 */
    public static final String AR_RECONCILE = "sys:crm:ar:reconcile";

    /** 查询工单（列表 / 详情） */
    public static final String TICKET_LIST = "sys:crm:ticket:list";

    /** 新建工单 */
    public static final String TICKET_CREATE = "sys:crm:ticket:create";

    /** 工单分派 */
    public static final String TICKET_ASSIGN = "sys:crm:ticket:assign";

    /** 工单处理（处理 / 备注 / 暂停 / 恢复 / 升级 / 解决 / 关闭 / 重开 / 取消） */
    public static final String TICKET_HANDLE = "sys:crm:ticket:handle";

    /** SLA 策略与工作日历维护 */
    public static final String SERVICE_SLA_MANAGE = "sys:crm:service:sla:manage";

    /** 查询知识引用（M4-4b） */
    public static final String KB_LIST = "sys:crm:kb:list";

    /** 维护知识引用（M4-4b） */
    public static final String KB_MANAGE = "sys:crm:kb:manage";

    /** 满意度问卷管理与结果查询（M4-4b） */
    public static final String SURVEY_MANAGE = "sys:crm:survey:manage";

    /** 查询退换货申请（M4-4c） */
    public static final String RETURN_LIST = "sys:crm:return:list";

    /** 维护退换货申请（M4-4c） */
    public static final String RETURN_MANAGE = "sys:crm:return:manage";

    /** 客户合并预览与执行（M1-4） */
    public static final String CUSTOMER_MERGE = "sys:crm:customer:merge";

    /** CSV 客户导入（M1-9） */
    public static final String CUSTOMER_IMPORT = "sys:crm:customer:import";

    /** CSV 客户导出（M1-9） */
    public static final String CUSTOMER_EXPORT = "sys:crm:customer:export";

    /** 查询营销活动 / 培育 / 触达（M5） */
    public static final String CAMPAIGN_LIST = "sys:crm:campaign:list";

    /** 新建与编辑营销活动（M5） */
    public static final String CAMPAIGN_MANAGE = "sys:crm:campaign:manage";

    /** 活动人群预览与锁定（M5） */
    public static final String CAMPAIGN_AUDIENCE = "sys:crm:campaign:audience";

    /** 活动启动 / 结束与 ROI 回填（M5） */
    public static final String CAMPAIGN_STATUS = "sys:crm:campaign:status";

    /** 培育任务维护（M5） */
    public static final String NURTURE_MANAGE = "sys:crm:nurture:manage";

    /** 批量触达任务创建与执行（M5） */
    public static final String MARKETING_SEND = "sys:crm:marketing:send";

    /** 渠道伙伴维护（M5） */
    public static final String PARTNER_MANAGE = "sys:crm:partner:manage";

    /** 渠道报单与冲突裁决（M5） */
    public static final String PARTNER_REPORT = "sys:crm:partner:report";

    /** 销售看板（M6） */
    public static final String ANALYTICS_DASHBOARD = "sys:crm:analytics:dashboard";

    /** 漏斗分析（M6） */
    public static final String ANALYTICS_FUNNEL = "sys:crm:analytics:funnel";

    /** 客户分析（M6） */
    public static final String ANALYTICS_CUSTOMER = "sys:crm:analytics:customer";

    /** 销售排行（M6） */
    public static final String ANALYTICS_RANKING = "sys:crm:analytics:ranking";

    /** 回款分析（M6） */
    public static final String ANALYTICS_COLLECTION = "sys:crm:analytics:collection";

    /** 行动计划与行动项（M6） */
    public static final String ACTION_PLAN = "sys:crm:action:plan";

    /** 分析导出（M6） */
    public static final String ANALYTICS_EXPORT = "sys:crm:export";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private CrmPermissions() {
    }
}
