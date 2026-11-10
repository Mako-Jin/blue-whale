package com.bluewhale.lms.course.application.view;

import com.bluewhale.lms.course.domain.model.Enrollment;
import com.bluewhale.lms.course.domain.model.LearningProgress;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 我的学习视图（报名 + 整体进度）。
 *
 * @author BlueWhale
 */
@Data
public class EnrollmentView {

    /** 主键 */
    private Long id;

    /** 课程 ID */
    private Long courseId;

    /** 课程名称（展示用，由应用层填充） */
    private String courseTitle;

    /** 课程状态 */
    private String courseStatus;

    /** 报名时间 */
    private LocalDateTime enrolledAt;

    /** 课程整体完成百分比（章节均值） */
    private Integer overallPercent;

    /** 章节进度明细 */
    private List<ChapterProgressView> chapters;

    /**
     * 从领域模型转换（不含进度，进度由应用层填充）。
     *
     * @param enrollment 报名记录
     * @return 视图
     */
    public static EnrollmentView from(Enrollment enrollment) {
        EnrollmentView view = new EnrollmentView();
        view.setId(enrollment.getId());
        view.setCourseId(enrollment.getCourseId());
        view.setEnrolledAt(enrollment.getEnrolledAt());
        return view;
    }

    /**
     * 章节进度视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class ChapterProgressView {

        /** 章节 ID */
        private Long chapterId;

        /** 章节标题 */
        private String chapterTitle;

        /** 完成百分比 */
        private Integer percent;

        /**
         * 构造章节进度视图。
         *
         * @param chapterId 章节 ID
         * @param chapterTitle 章节标题
         * @param progress 进度（可为 null）
         * @return 视图
         */
        public static ChapterProgressView from(Long chapterId, String chapterTitle, LearningProgress progress) {
            ChapterProgressView view = new ChapterProgressView();
            view.setChapterId(chapterId);
            view.setChapterTitle(chapterTitle);
            view.setPercent(progress == null ? 0 : progress.getPercent());
            return view;
        }
    }
}
