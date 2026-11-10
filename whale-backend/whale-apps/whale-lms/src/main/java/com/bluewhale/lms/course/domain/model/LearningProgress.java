package com.bluewhale.lms.course.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

/**
 * 章节学习进度。
 *
 * <p>进度只增不减：重复上报取历史与本次的较大值（重学可覆盖为更优值）。</p>
 *
 * @author BlueWhale
 */
@Data
public class LearningProgress {

    /** 主键 */
    private Long id;

    /** 学习记录 ID */
    private Long enrollmentId;

    /** 章节 ID */
    private Long chapterId;

    /** 完成百分比（0–100） */
    private Integer percent;

    /** 最近学习时间 */
    private java.time.LocalDateTime lastLearnedAt;

    /**
     * 合并上报进度：只增不减。
     *
     * @param reported 本次上报百分比（0–100）
     */
    public void merge(int reported) {
        if (reported < 0 || reported > 100) {
            throw LmsErrorCode.exception(LmsErrorCode.PROGRESS_INVALID, "学习进度必须在 0–100 之间");
        }
        percent = Math.max(percent == null ? 0 : percent, reported);
    }
}
