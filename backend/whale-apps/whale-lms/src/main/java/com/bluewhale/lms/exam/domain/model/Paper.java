package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 试卷聚合根。
 *
 * <p>总分与题目数由题目明细重算回写（{@link #applyTotals}），不接受前端直填，
 * 避免「总分与明细不一致」这类只能在考试当天才被发现的问题。</p>
 *
 * @author BlueWhale
 */
@Data
public class Paper {

    /** 编码上限 */
    private static final int CODE_MAX_LENGTH = 64;

    /** 名称上限 */
    private static final int TITLE_MAX_LENGTH = 128;

    /** 备注上限 */
    private static final int REMARK_MAX_LENGTH = 500;

    /** 时长上限（分钟） */
    private static final int DURATION_MAX_MINUTES = 600;

    /** 时长下限（分钟） */
    private static final int DURATION_MIN_MINUTES = 1;

    /** 主键 */
    private Long id;

    /** 试卷编码（租户内唯一） */
    private String paperCode;

    /** 试卷名称 */
    private String title;

    /** 关联课程 ID（可空） */
    private Long courseId;

    /** 考试时长（分钟） */
    private Integer durationMinutes;

    /** 总分（明细累加） */
    private BigDecimal totalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 题目数（明细计数） */
    private Integer questionCount;

    /** 抽题种子 */
    private String seed;

    /** 抽题规则 JSON 快照 */
    private String ruleConfig;

    /** 状态 */
    private PaperStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 断言可编辑内容与题目。
     */
    public void assertEditable() {
        if (status != null && !status.canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT,
                    "已发布试卷的题目不可变更，请先归档后另建试卷");
        }
    }

    /**
     * 基础字段校验。
     */
    public void validateBasics() {
        if (title == null || title.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID, "试卷名称不能为空");
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                    "试卷名称不得超过 " + TITLE_MAX_LENGTH + " 字符");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
        if (durationMinutes == null || durationMinutes < DURATION_MIN_MINUTES
                || durationMinutes > DURATION_MAX_MINUTES) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                    "考试时长必须在 " + DURATION_MIN_MINUTES + "–" + DURATION_MAX_MINUTES + " 分钟之间");
        }
        if (passScore == null || passScore.compareTo(BigDecimal.ZERO) < 0) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID, "及格分不能为负");
        }
    }

    /**
     * 发布试卷：至少 1 题、总分大于 0、及格分不超过总分。
     *
     * @param computedTotal 明细累加总分
     * @param count 题目数
     */
    public void publish(BigDecimal computedTotal, int count) {
        if (status != null && !status.canPublish()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT, "仅草稿试卷可发布");
        }
        applyTotals(computedTotal, count);
        if (count < 1) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT, "发布前试卷至少需要 1 道题");
        }
        if (totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_STATE_CONFLICT, "试卷总分必须大于 0");
        }
        if (passScore.compareTo(totalScore) > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                    "及格分 " + passScore + " 不得超过总分 " + totalScore);
        }
        status = PaperStatus.PUBLISHED;
    }

    /**
     * 回写明细汇总值。
     *
     * @param computedTotal 明细累加总分
     * @param count 题目数
     */
    public void applyTotals(BigDecimal computedTotal, int count) {
        totalScore = computedTotal == null ? BigDecimal.ZERO : computedTotal;
        questionCount = count;
    }

    /**
     * 规范化试卷编码：去空白 + 大写。
     *
     * @param raw 原始编码
     * @return 规范化编码
     */
    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID, "试卷编码不能为空");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.length() > CODE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.PAPER_INVALID,
                    "试卷编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        return value;
    }
}
