package com.bluewhale.crm.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * CRM 业务错误码。
 *
 * <p>段位约定：{@code 410xx} 参数与业务校验失败，{@code 410xx} 中 41001–41009
 * 为资源与状态冲突（跨租户一律按不存在处理），41010 起为公海、画像与权限范围。</p>
 *
 * @author BlueWhale
 */
public final class CrmErrorCode {

    /** 需求门禁未通过，业务接口未开放 */
    public static final int GATE_CLOSED = 41000;

    /** 客户不存在或无权访问 */
    public static final int CUSTOMER_NOT_FOUND = 41001;

    /** 客户编码已存在 */
    public static final int CUSTOMER_CODE_CONFLICT = 41002;

    /** 客户硬冲突：名称、税号或域名重复，禁止保存 */
    public static final int CUSTOMER_HARD_DUPLICATE = 41003;

    /** 客户软冲突：电话或邮箱疑似重复，确认后可保存 */
    public static final int CUSTOMER_SOFT_DUPLICATE = 41004;

    /** 客户状态不允许该操作 */
    public static final int CUSTOMER_STATE_CONFLICT = 41005;

    /** 客户等级非法，只允许 A/B/C/D */
    public static final int CUSTOMER_LEVEL_INVALID = 41006;

    /** 客户已合并，只读 */
    public static final int CUSTOMER_MERGED_READONLY = 41007;

    /** 联系人不存在或无权访问 */
    public static final int CONTACT_NOT_FOUND = 41008;

    /** 主联系人唯一约束冲突 */
    public static final int CONTACT_PRIMARY_CONFLICT = 41009;

    /** 地址不存在或无权访问 */
    public static final int ADDRESS_NOT_FOUND = 41010;

    /** 客户画像参数非法（决策链联系人不属于该客户等） */
    public static final int PROFILE_INVALID = 41011;

    /** 客户不在公海 */
    public static final int POOL_NOT_IN_POOL = 41012;

    /** 客户已被他人领取 */
    public static final int POOL_CLAIM_CONFLICT = 41013;

    /** 超过每日领取上限 */
    public static final int POOL_CLAIM_LIMIT = 41014;

    /** 公海规则不存在 */
    public static final int POOL_RULE_NOT_FOUND = 41015;

    /** 跟进记录必须指定客户 */
    public static final int FOLLOW_UP_CUSTOMER_REQUIRED = 41016;

    /** 数据范围越权（按不存在处理，避免探测） */
    public static final int DATA_SCOPE_DENIED = 41017;

    /** 缺少可信身份头（必须经网关访问） */
    public static final int ACTOR_REQUIRED = 41018;

    /** 通用参数非法 */
    public static final int PARAM_INVALID = 41019;

    /** 分配客户时必须指定负责人 */
    public static final int CUSTOMER_OWNER_REQUIRED = 41020;

    /** 线索不存在或无权访问 */
    public static final int LEAD_NOT_FOUND = 41021;

    /** 线索疑似重复（电话或邮箱相同），确认后可保存 */
    public static final int LEAD_DUPLICATE = 41022;

    /** 线索状态不允许该操作 */
    public static final int LEAD_STATE_CONFLICT = 41023;

    /** 标记线索无效必须填写原因 */
    public static final int LEAD_INVALID_REASON_REQUIRED = 41024;

    /** 线索已转化，不可重复转化 */
    public static final int LEAD_ALREADY_CONVERTED = 41025;

    /** 商机不存在或无权访问 */
    public static final int OPPORTUNITY_NOT_FOUND = 41026;

    /** 商机阶段流转非法 */
    public static final int OPPORTUNITY_STAGE_CONFLICT = 41027;

    /** 赢单商机必须填写金额 */
    public static final int OPPORTUNITY_AMOUNT_REQUIRED = 41028;

    /** 输单商机必须填写原因 */
    public static final int OPPORTUNITY_LOST_REASON_REQUIRED = 41029;

    /** 商机必须归属客户 */
    public static final int OPPORTUNITY_CUSTOMER_REQUIRED = 41030;

    /** 报价不存在或无权访问 */
    public static final int QUOTE_NOT_FOUND = 41031;

    /** 报价状态不允许该操作 */
    public static final int QUOTE_STATE_CONFLICT = 41032;

    /** 报价至少需要一行明细 */
    public static final int QUOTE_LINE_REQUIRED = 41033;

    /** 报价金额或折扣非法 */
    public static final int QUOTE_AMOUNT_INVALID = 41034;

    /** 报价审批不可用（未启用或缺少流程定义） */
    public static final int QUOTE_APPROVAL_UNAVAILABLE = 41035;

    /** 报价审批状态非法 */
    public static final int QUOTE_APPROVAL_STATE_CONFLICT = 41036;

    /** 预测快照不存在或无权访问 */
    public static final int FORECAST_NOT_FOUND = 41037;

    /** 预测周期非法（月为 yyyy-MM，季为 yyyy-Qn） */
    public static final int FORECAST_PERIOD_INVALID = 41038;

    /** 预测调整参数非法（字段不支持或金额为负） */
    public static final int FORECAST_ADJUST_INVALID = 41039;

    /** 销售目标不存在或无权访问 */
    public static final int TARGET_NOT_FOUND = 41040;

    /** 同一周期同一范围的目标已存在 */
    public static final int TARGET_DUPLICATE = 41041;

    /** 销售目标金额非法 */
    public static final int TARGET_AMOUNT_INVALID = 41042;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(GATE_CLOSED, "需求门禁未通过，CRM 业务接口尚未开放"),
            Map.entry(CUSTOMER_NOT_FOUND, "客户不存在或无权访问"),
            Map.entry(CUSTOMER_CODE_CONFLICT, "客户编码已存在，请更换后重试"),
            Map.entry(CUSTOMER_HARD_DUPLICATE, "存在同名、同税号或同域名的客户，禁止重复创建"),
            Map.entry(CUSTOMER_SOFT_DUPLICATE, "存在疑似重复客户（电话或邮箱相同），请确认后再保存"),
            Map.entry(CUSTOMER_STATE_CONFLICT, "客户当前状态不允许该操作"),
            Map.entry(CUSTOMER_LEVEL_INVALID, "客户等级只允许 A/B/C/D"),
            Map.entry(CUSTOMER_MERGED_READONLY, "客户已合并，只允许查看"),
            Map.entry(CONTACT_NOT_FOUND, "联系人不存在或无权访问"),
            Map.entry(CONTACT_PRIMARY_CONFLICT, "每个客户最多一个主联系人，请先调整其他联系人"),
            Map.entry(ADDRESS_NOT_FOUND, "客户地址不存在或无权访问"),
            Map.entry(PROFILE_INVALID, "客户画像参数非法，请检查决策链联系人"),
            Map.entry(POOL_NOT_IN_POOL, "客户当前不在公海"),
            Map.entry(POOL_CLAIM_CONFLICT, "客户已被他人领取，请刷新后重试"),
            Map.entry(POOL_CLAIM_LIMIT, "已超过每日领取上限"),
            Map.entry(POOL_RULE_NOT_FOUND, "公海规则不存在"),
            Map.entry(FOLLOW_UP_CUSTOMER_REQUIRED, "跟进记录必须指定客户"),
            Map.entry(DATA_SCOPE_DENIED, "无权访问该客户"),
            Map.entry(ACTOR_REQUIRED, "缺少可信身份上下文，请通过网关访问"),
            Map.entry(PARAM_INVALID, "请求参数非法"),
            Map.entry(CUSTOMER_OWNER_REQUIRED, "分配客户时必须指定负责人"),
            Map.entry(LEAD_NOT_FOUND, "线索不存在或无权访问"),
            Map.entry(LEAD_DUPLICATE, "存在疑似重复线索（电话或邮箱相同），请确认后再保存"),
            Map.entry(LEAD_STATE_CONFLICT, "线索当前状态不允许该操作"),
            Map.entry(LEAD_INVALID_REASON_REQUIRED, "标记线索无效时必须填写原因"),
            Map.entry(LEAD_ALREADY_CONVERTED, "线索已转化，不可重复转化"),
            Map.entry(OPPORTUNITY_NOT_FOUND, "商机不存在或无权访问"),
            Map.entry(OPPORTUNITY_STAGE_CONFLICT, "商机阶段流转非法，请按 CONTACT → PROPOSAL → NEGOTIATION → WON 顺序推进"),
            Map.entry(OPPORTUNITY_AMOUNT_REQUIRED, "赢单商机必须填写金额"),
            Map.entry(OPPORTUNITY_LOST_REASON_REQUIRED, "输单商机必须填写原因"),
            Map.entry(OPPORTUNITY_CUSTOMER_REQUIRED, "商机必须归属客户"),
            Map.entry(QUOTE_NOT_FOUND, "报价不存在或无权访问"),
            Map.entry(QUOTE_STATE_CONFLICT, "报价当前状态不允许该操作"),
            Map.entry(QUOTE_LINE_REQUIRED, "报价至少需要一行明细"),
            Map.entry(QUOTE_AMOUNT_INVALID, "报价金额或折扣非法，请检查数量、单价与折扣"),
            Map.entry(QUOTE_APPROVAL_UNAVAILABLE, "报价审批未启用或缺少流程定义，请联系管理员"),
            Map.entry(QUOTE_APPROVAL_STATE_CONFLICT, "报价审批状态非法"),
            Map.entry(FORECAST_NOT_FOUND, "预测快照不存在或无权访问"),
            Map.entry(FORECAST_PERIOD_INVALID, "预测周期非法，月周期为 yyyy-MM，季周期为 yyyy-Qn"),
            Map.entry(FORECAST_ADJUST_INVALID, "预测调整参数非法，请检查调整字段与金额"),
            Map.entry(TARGET_NOT_FOUND, "销售目标不存在或无权访问"),
            Map.entry(TARGET_DUPLICATE, "同一周期同一范围的销售目标已存在"),
            Map.entry(TARGET_AMOUNT_INVALID, "销售目标金额不得为负数"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private CrmErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "CRM 操作失败");
    }

    /**
     * 以错误码构造业务异常。
     *
     * @param code 业务错误码
     * @return 业务异常
     */
    public static BizException exception(int code) {
        return new BizException(code, messageOf(code));
    }

    /**
     * 以错误码与自定义消息构造业务异常。
     *
     * @param code 业务错误码
     * @param message 自定义提示语
     * @return 业务异常
     */
    public static BizException exception(int code, String message) {
        return new BizException(code, message);
    }
}
