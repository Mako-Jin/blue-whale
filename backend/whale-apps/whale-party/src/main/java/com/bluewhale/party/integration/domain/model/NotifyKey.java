package com.bluewhale.party.integration.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 通知幂等键拼装（M-P4）。
 *
 * <p>键只表达「同一件事」，不含接收人：接收人由 {@code pty_notify_log} 唯一键的
 * {@code user_id} 一起裁定。把 userId 拼进键就回答不了「这件事发了几轮」，
 * 而运维排查恰恰需要后者。</p>
 *
 * @author BlueWhale
 */
public final class NotifyKey {

    /** 月份段落格式 */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 工具类禁止实例化。
     */
    private NotifyKey() {
    }

    /**
     * 转正提醒键（按月，一月最多一次）。
     *
     * @param memberId 党员档案 ID
     * @param day 基准日
     * @return 幂等键
     */
    public static String probation(Long memberId, LocalDate day) {
        LocalDate date = day == null ? LocalDate.now() : day;
        return "PARTY_PROBATION:" + required(memberId, "党员") + ":" + MONTH.format(date);
    }

    /**
     * 党费催缴键（每张账单一次）。
     *
     * @param billId 账单 ID
     * @return 幂等键
     */
    public static String fee(Long billId) {
        return "PARTY_FEE:" + required(billId, "党费账单");
    }

    /**
     * 组织生活通知键。
     *
     * @param activityId 组织生活 ID
     * @return 幂等键
     */
    public static String partyActivity(Long activityId) {
        return "PARTY_ACTIVITY:" + required(activityId, "组织生活");
    }

    /**
     * 支部换届提醒键（每届一次，届期年份决定唯一性）。
     *
     * @param branchId 支部 ID
     * @param deadlineYear 应换届年份
     * @return 幂等键
     */
    public static String branchTerm(Long branchId, int deadlineYear) {
        return "PARTY_BRANCH_TERM:" + required(branchId, "支部") + ":" + deadlineYear;
    }

    /**
     * 工会活动通知键。
     *
     * @param activityId 工会活动 ID
     * @return 幂等键
     */
    public static String unionActivity(Long activityId) {
        return "UNION_ACTIVITY:" + required(activityId, "工会活动");
    }

    /**
     * 提案反馈通知键。
     *
     * @param proposalId 提案 ID
     * @return 幂等键
     */
    public static String proposalFeedback(Long proposalId) {
        return "UNION_PROPOSAL_FEEDBACK:" + required(proposalId, "提案");
    }

    /**
     * 校验业务 ID。
     *
     * @param value 业务 ID
     * @param label 业务名称
     * @return 原值
     */
    private static Long required(Long value, String label) {
        if (value == null) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SCENE_INVALID,
                    label + " ID 不能为空，无法生成通知幂等键");
        }
        return value;
    }
}
