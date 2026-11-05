package com.bluewhale.oa.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * OA 业务错误码。
 *
 * <p>段位约定：{@code 400xx} 参数与业务校验失败，{@code 404xx} 资源不存在
 * （跨租户一律按不存在处理），{@code 409xx} 状态或唯一性冲突。
 * 存量请假接口继续使用通用 {@code 400/404} 错误码，保证旧前端行为不变。</p>
 *
 * @author BlueWhale
 */
public final class OaErrorCode {

    /** 审批配置不存在 */
    public static final int APPROVAL_CONFIG_NOT_FOUND = 40401;

    /** 审批配置业务类型已存在 */
    public static final int APPROVAL_CONFIG_EXISTS = 40901;

    /** 审批配置参数非法 */
    public static final int APPROVAL_CONFIG_INVALID = 40001;

    /** 审批实例不存在 */
    public static final int APPROVAL_INSTANCE_NOT_FOUND = 40402;

    /** 审批实例状态不允许该操作 */
    public static final int APPROVAL_STATUS_CONFLICT = 40902;

    /** 审批结果非法 */
    public static final int APPROVAL_RESULT_INVALID = 40002;

    /** 审批实例缺少 Workflow 实例ID */
    public static final int APPROVAL_WORKFLOW_NOT_BOUND = 40903;

    /** 审批人规则不存在 */
    public static final int APPROVAL_RULE_NOT_FOUND = 40403;

    /** 审批人规则参数非法 */
    public static final int APPROVAL_RULE_INVALID = 40003;

    /** 审批副作用不存在 */
    public static final int APPROVAL_EFFECT_NOT_FOUND = 40404;

    /** 审批副作用已结束，不能重复执行 */
    public static final int APPROVAL_EFFECT_CLOSED = 40904;

    /** 文件引用已存在 */
    public static final int FILE_REF_EXISTS = 40905;

    /** 文件引用不存在 */
    public static final int FILE_REF_NOT_FOUND = 40405;

    /** 文件引用参数非法 */
    public static final int FILE_REF_INVALID = 40004;

    /** 序列号参数非法 */
    public static final int SEQUENCE_INVALID = 40005;

    /** Outbox 事件已存在 */
    public static final int OUTBOX_EVENT_EXISTS = 40906;

    /** Outbox 事件不存在 */
    public static final int OUTBOX_EVENT_NOT_FOUND = 40406;

    /** 员工档案不存在 */
    public static final int EMPLOYEE_PROFILE_NOT_FOUND = 40407;

    /** 员工档案参数非法 */
    public static final int EMPLOYEE_PROFILE_INVALID = 40006;

    /** 假期类型不存在 */
    public static final int LEAVE_TYPE_NOT_FOUND = 40408;

    /** 假期类型编码已存在 */
    public static final int LEAVE_TYPE_EXISTS = 40907;

    /** 假期类型参数非法 */
    public static final int LEAVE_TYPE_INVALID = 40007;

    /** 假期额度不存在 */
    public static final int LEAVE_BALANCE_NOT_FOUND = 40409;

    /** 假期额度不足 */
    public static final int LEAVE_BALANCE_INSUFFICIENT = 40908;

    /** 假期额度参数非法 */
    public static final int LEAVE_BALANCE_INVALID = 40008;

    /** 请假申请不存在 */
    public static final int LEAVE_NOT_FOUND = 40410;

    /** 请假申请状态不允许该操作 */
    public static final int LEAVE_STATUS_CONFLICT = 40909;

    /** 请假时间区间与其他申请重叠 */
    public static final int LEAVE_PERIOD_OVERLAP = 40910;

    /** 请假参数非法 */
    public static final int LEAVE_INVALID = 40009;

    /** 工作日历参数非法 */
    public static final int WORK_CALENDAR_INVALID = 40010;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(APPROVAL_CONFIG_NOT_FOUND, "审批配置不存在或无权访问"),
            Map.entry(APPROVAL_CONFIG_EXISTS, "该业务类型已存在审批配置"),
            Map.entry(APPROVAL_CONFIG_INVALID, "审批配置参数非法，请检查业务类型与流程定义"),
            Map.entry(APPROVAL_INSTANCE_NOT_FOUND, "审批实例不存在或无权访问"),
            Map.entry(APPROVAL_STATUS_CONFLICT, "审批实例当前状态不允许该操作"),
            Map.entry(APPROVAL_RESULT_INVALID, "审批结果仅支持 APPROVED / REJECTED / CANCELLED / BROKEN"),
            Map.entry(APPROVAL_WORKFLOW_NOT_BOUND, "审批实例尚未绑定 Workflow 实例，请先发起流程"),
            Map.entry(APPROVAL_RULE_NOT_FOUND, "审批人规则不存在或无权访问"),
            Map.entry(APPROVAL_RULE_INVALID, "审批人规则参数非法，请检查节点与解析器"),
            Map.entry(APPROVAL_EFFECT_NOT_FOUND, "审批副作用不存在或无权访问"),
            Map.entry(APPROVAL_EFFECT_CLOSED, "审批副作用已结束，不能重复执行"),
            Map.entry(FILE_REF_EXISTS, "该文件已关联到当前业务"),
            Map.entry(FILE_REF_NOT_FOUND, "文件引用不存在或无权访问"),
            Map.entry(FILE_REF_INVALID, "文件引用参数非法"),
            Map.entry(SEQUENCE_INVALID, "序列号参数非法"),
            Map.entry(OUTBOX_EVENT_EXISTS, "集成事件已存在"),
            Map.entry(OUTBOX_EVENT_NOT_FOUND, "集成事件不存在或无权访问"),
            Map.entry(EMPLOYEE_PROFILE_NOT_FOUND, "员工档案不存在或无权访问"),
            Map.entry(EMPLOYEE_PROFILE_INVALID, "员工档案参数非法，请检查用户与在职状态"),
            Map.entry(LEAVE_TYPE_NOT_FOUND, "假期类型不存在或无权访问"),
            Map.entry(LEAVE_TYPE_EXISTS, "假期类型编码已存在"),
            Map.entry(LEAVE_TYPE_INVALID, "假期类型参数非法，请检查编码/名称/单位与额度"),
            Map.entry(LEAVE_BALANCE_NOT_FOUND, "假期额度不存在或无权访问"),
            Map.entry(LEAVE_BALANCE_INSUFFICIENT, "假期额度不足，无法提交申请"),
            Map.entry(LEAVE_BALANCE_INVALID, "假期额度参数非法"),
            Map.entry(LEAVE_NOT_FOUND, "请假申请不存在或无权访问"),
            Map.entry(LEAVE_STATUS_CONFLICT, "请假申请当前状态不允许该操作"),
            Map.entry(LEAVE_PERIOD_OVERLAP, "同一假期类型在所选时段内已有未结束的申请"),
            Map.entry(LEAVE_INVALID, "请假参数非法，请检查日期、时段与假期类型"),
            Map.entry(WORK_CALENDAR_INVALID, "工作日历参数非法，请检查日期与工时"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private OaErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "OA 操作失败");
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
