package com.bluewhale.lms.exam.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 考试聚合根。
 *
 * <p>考试引用已发布试卷，自身只管「什么时候考、能考几次、多久、是否乱序」。
 * 窗口与单次时长相互独立：单次截止取 {@code min(开始 + 时长, 考试截止时间)}，
 * 避免「22:00 开始、60 分钟」的作答拖到第二天。</p>
 *
 * @author BlueWhale
 */
@Data
public class Exam {

    /** 编码上限 */
    private static final int CODE_MAX_LENGTH = 64;

    /** 名称上限 */
    private static final int TITLE_MAX_LENGTH = 128;

    /** 备注上限 */
    private static final int REMARK_MAX_LENGTH = 500;

    /** 时长下限（分钟） */
    private static final int DURATION_MIN_MINUTES = 1;

    /** 时长上限（分钟） */
    private static final int DURATION_MAX_MINUTES = 600;

    /** 主键 */
    private Long id;

    /** 考试编码（租户内唯一） */
    private String examCode;

    /** 考试名称 */
    private String title;

    /** 试卷 ID */
    private Long paperId;

    /** 关联课程 ID */
    private Long courseId;

    /** 开考时间 */
    private LocalDateTime startTime;

    /** 截止时间 */
    private LocalDateTime endTime;

    /** 单次作答时长（分钟） */
    private Integer durationMinutes;

    /** 最大作答次数 */
    private Integer maxAttempts;

    /** 是否乱序 */
    private boolean shuffle;

    /** 及格分 */
    private BigDecimal passScore;

    /** 切屏次数上限（0 不限） */
    private Integer switchLimit;

    /** 状态 */
    private ExamStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 断言可编辑配置。
     */
    public void assertEditable() {
        if (status != null && !status.canEdit()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "已发布考试不可修改配置");
        }
    }

    /**
     * 校验时间窗与参数。
     */
    public void validateBasics() {
        validateTexts();
        validateWindow();
        validateLimits();
    }

    /**
     * 校验名称与备注长度。
     */
    private void validateTexts() {
        if (title == null || title.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "考试名称不能为空");
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID,
                    "考试名称不得超过 " + TITLE_MAX_LENGTH + " 字符");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 校验考试时间窗。
     */
    private void validateWindow() {
        if (startTime == null || endTime == null) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "开考与截止时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "截止时间必须晚于开考时间");
        }
    }

    /**
     * 校验时长、次数、及格分与切屏上限。
     */
    private void validateLimits() {
        if (durationMinutes == null || durationMinutes < DURATION_MIN_MINUTES
                || durationMinutes > DURATION_MAX_MINUTES) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID,
                    "单次作答时长必须在 " + DURATION_MIN_MINUTES + "–" + DURATION_MAX_MINUTES + " 分钟之间");
        }
        if (maxAttempts == null || maxAttempts < 1) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "最大作答次数必须大于等于 1");
        }
        if (passScore == null || passScore.compareTo(BigDecimal.ZERO) < 0) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "及格分不能为负");
        }
        if (switchLimit == null || switchLimit < 0) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "切屏次数上限不能为负");
        }
    }

    /**
     * 发布考试。
     */
    public void publish() {
        if (status != null && !status.canEdit()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "仅草稿考试可发布");
        }
        validateBasics();
        status = ExamStatus.PUBLISHED;
    }

    /**
     * 结课。
     */
    public void close() {
        if (status == null || !status.canAttempt()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "仅已发布考试可结课");
        }
        status = ExamStatus.CLOSED;
    }

    /**
     * 断言当前可作答（状态与时间窗）。
     *
     * @param now 当前时刻
     */
    public void assertOpen(LocalDateTime now) {
        if (status == null || !status.canAttempt()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_STATE_CONFLICT, "考试未发布或已结课");
        }
        if (now.isBefore(startTime)) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_WINDOW_CLOSED, "考试尚未开始");
        }
        if (now.isAfter(endTime)) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_WINDOW_CLOSED, "考试已截止");
        }
    }

    /**
     * 计算本次作答截止时间：min(开始 + 时长, 考试截止)。
     *
     * @param startedAt 开始作答时间
     * @return 截止时间
     */
    public LocalDateTime deadlineOf(LocalDateTime startedAt) {
        LocalDateTime byDuration = startedAt.plusMinutes(durationMinutes.longValue());
        return byDuration.isBefore(endTime) ? byDuration : endTime;
    }

    /**
     * 规范化考试编码。
     *
     * @param raw 原始编码
     * @return 规范化编码
     */
    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID, "考试编码不能为空");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.length() > CODE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.EXAM_INVALID,
                    "考试编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        return value;
    }
}
