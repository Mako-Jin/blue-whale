package com.bluewhale.party.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 党政工会业务错误码。
 *
 * <p>段位约定：{@code 43xxx} 归 {@code whale-party}，与 LMS 的 42xxx 并列不交叉。
 * 43000–43009 为门禁、身份、编号流水与支部 / 党员 / 组织生活基础；
 * 43010–43029 为党费与党建台账其余；43030–43049 为党建内容与先审后发；
 * 43050–43079 为工会（会员 / 慰问 / 福利 / 活动 / 提案）；
 * 43080–43089 为跨服务契约（ERP / 文件 / 通知 / 审批）；43090 起留给看板与后续上下文。</p>
 *
 * @author BlueWhale
 */
public final class PartyErrorCode {

    /** 需求门禁未通过，业务接口未开放 */
    public static final int GATE_CLOSED = 43000;

    /** 缺少可信操作人身份，写操作失败关闭 */
    public static final int ACTOR_REQUIRED = 43001;

    /** 业务编号取号参数非法（前缀为空、租户缺失或取号次数耗尽） */
    public static final int SEQUENCE_INVALID = 43002;

    /** 支部不存在或无权访问（跨租户一律按不存在处理） */
    public static final int BRANCH_NOT_FOUND = 43003;

    /** 支部编码已存在 */
    public static final int BRANCH_CODE_CONFLICT = 43004;

    /** 上级支部非法：自指或形成环 */
    public static final int BRANCH_PARENT_CYCLE = 43005;

    /** 党员档案不存在或无权访问 */
    public static final int MEMBER_NOT_FOUND = 43006;

    /** 该员工已有党员档案（一人一档） */
    public static final int MEMBER_DUPLICATED = 43007;

    /** 组织生活记录不存在或无权访问 */
    public static final int ACTIVITY_NOT_FOUND = 43008;

    /** 组织生活状态不允许该操作（已归档不可再改记录） */
    public static final int ACTIVITY_STATE_CONFLICT = 43009;

    /** 党费参数非法（基数、比例或所属月份越界） */
    public static final int FEE_INVALID = 43010;

    /** 业务错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(GATE_CLOSED, "需求门禁未通过，业务接口未开放"),
            Map.entry(ACTOR_REQUIRED, "缺少操作人身份，无法执行该操作"),
            Map.entry(SEQUENCE_INVALID, "业务编号生成失败，请检查编号规则"),
            Map.entry(BRANCH_NOT_FOUND, "支部不存在或无权访问"),
            Map.entry(BRANCH_CODE_CONFLICT, "支部编码已存在"),
            Map.entry(BRANCH_PARENT_CYCLE, "上级支部不能是自身或其下级"),
            Map.entry(MEMBER_NOT_FOUND, "党员档案不存在或无权访问"),
            Map.entry(MEMBER_DUPLICATED, "该员工已有党员档案"),
            Map.entry(ACTIVITY_NOT_FOUND, "组织生活记录不存在或无权访问"),
            Map.entry(ACTIVITY_STATE_CONFLICT, "组织生活记录当前状态不允许该操作"),
            Map.entry(FEE_INVALID, "党费参数不合法"));

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private PartyErrorCode() {
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

    /**
     * 错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 提示语
     */
    private static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "党政工会业务校验失败");
    }
}
