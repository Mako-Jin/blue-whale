package com.bluewhale.lms.credit.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 学分规则。
 *
 * <p>{@link #matchId} 为 {@code 0} 表示该来源下的全部对象（通配），大于 0 表示只对某个
 * 课程 / 考试 / 培训班生效。匹配优先级很直接：先找精确规则，没有再用通配规则——
 * 这样「公司统一：任一考试通过计 2 学分」与「安全生产考试计 5 学分」可以共存。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditRule {

    /** 规则编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 规则名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 单次学分上限（一门课给到 999 学分基本是误填） */
    public static final BigDecimal CREDIT_MAX = new BigDecimal("999.9");

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** 通配匹配标识：对该来源下的全部对象生效 */
    public static final long MATCH_ALL = 0L;

    /** 主键 */
    private Long id;

    /** 规则编码（租户内唯一，大写归一） */
    private String ruleCode;

    /** 规则名称 */
    private String name;

    /** 来源类型 */
    private CreditSource sourceType;

    /** 匹配对象 ID（0 为该来源全部） */
    private Long matchId;

    /** 单次学分 */
    private BigDecimal credit;

    /** 状态 */
    private CreditRuleStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 归一规则编码：去空白并转大写。
     *
     * @param code 原始编码
     * @return 归一后的编码
     */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 校验规则字段。
     */
    public void validateBasics() {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID, "规则编码不能为空");
        }
        if (ruleCode.length() > CODE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID,
                    "规则编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (name == null || name.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID, "规则名称不能为空");
        }
        if (name.trim().length() > NAME_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID,
                    "规则名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (sourceType == null) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID, "学分来源不能为空");
        }
        if (matchId == null || matchId < MATCH_ALL) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID, "匹配对象 ID 不能为负");
        }
        if (credit == null || credit.compareTo(BigDecimal.ZERO) <= 0
                || credit.compareTo(CREDIT_MAX) > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID,
                    "单次学分必须大于 0 且不超过 " + CREDIT_MAX.toPlainString());
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 确认规则可用于记账。
     */
    public void requireAccruable() {
        if (status == null || !status.canAccrue()) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_DISABLED);
        }
    }

    /**
     * 是否为该来源下的通配规则。
     *
     * @return 通配返回 true
     */
    public boolean isMatchAll() {
        return matchId == null || matchId == MATCH_ALL;
    }
}
