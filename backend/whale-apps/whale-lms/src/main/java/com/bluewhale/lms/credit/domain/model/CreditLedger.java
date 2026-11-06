package com.bluewhale.lms.credit.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学分台账（一次达成一条）。
 *
 * <p>{@link #periodYear} 单独落库而不是查询时按 {@link #occurredAt} 现算：跨年补记
 * （1 月补记去年 12 月的培训）必须归入业务发生年度，否则年度汇总会随补记时点漂移。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditLedger {

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 来源类型 */
    private CreditSource sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** 命中规则 ID */
    private Long ruleId;

    /** 命中规则编码快照 */
    private String ruleCode;

    /** 本次学分 */
    private BigDecimal credit;

    /** 业务发生时间 */
    private LocalDateTime occurredAt;

    /** 归属年度 */
    private Integer periodYear;

    /** 记账人 */
    private Long recordedBy;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 按规则折算生成台账记录。
     *
     * <p>折算只在这里发生一次：规则给多少就记多少，不在别处再乘系数，
     * 避免「规则 5 分、台账 10 分」这种事后对不上的情况。</p>
     *
     * @param rule 命中的规则
     * @param userId 学员用户 ID
     * @param username 学员用户名
     * @param sourceId 来源业务 ID
     * @param occurredAt 业务发生时间
     * @param recordedBy 记账人
     * @param remark 备注
     * @return 台账记录
     */
    public static CreditLedger of(CreditRule rule, Long userId, String username, Long sourceId,
                                  LocalDateTime occurredAt, Long recordedBy, String remark) {
        if (rule == null) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_MISSING);
        }
        if (userId == null || userId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_INVALID, "学员不能为空");
        }
        if (sourceId == null || sourceId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_INVALID, "来源业务 ID 不能为空");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        LocalDateTime occurred = occurredAt == null ? LocalDateTime.now() : occurredAt;
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(userId);
        ledger.setUsername(username);
        ledger.setSourceType(rule.getSourceType());
        ledger.setSourceId(sourceId);
        ledger.setRuleId(rule.getId());
        ledger.setRuleCode(rule.getRuleCode());
        ledger.setCredit(rule.getCredit());
        ledger.setOccurredAt(occurred);
        ledger.setPeriodYear(occurred.getYear());
        ledger.setRecordedBy(recordedBy);
        ledger.setRemark(remark);
        return ledger;
    }
}
