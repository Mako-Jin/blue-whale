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

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private CrmPermissions() {
    }
}
