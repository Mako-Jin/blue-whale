package com.bluewhale.lms.course.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 课程聚合根。
 *
 * <p>课程是 LMS 的学习主数据；课件字节归 {@code whale-file}，LMS 只保存引用；
 * 章节与课件属于本聚合内的实体。发布状态机见 {@link CourseStatus}。</p>
 *
 * @author BlueWhale
 */
@Data
public class Course {

    /** 主键 */
    private Long id;

    /** 课程编码（租户内唯一） */
    private String courseCode;

    /** 课程名称 */
    private String title;

    /** 分类 */
    private String category;

    /** 简介 */
    private String description;

    /** 讲师 */
    private String lecturer;

    /** 状态：DRAFT / PUBLISHED / ARCHIVED */
    private CourseStatus status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 发布课程：仅 DRAFT 可发布，且至少 1 个章节。
     *
     * @param chapterCount 当前章节数
     */
    public void publish(long chapterCount) {
        if (status != CourseStatus.DRAFT) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_STATE_CONFLICT, "仅草稿课程可发布");
        }
        if (chapterCount < 1) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_STATE_CONFLICT, "发布前至少配置 1 个章节");
        }
        status = CourseStatus.PUBLISHED;
    }

    /**
     * 归档下架：PUBLISHED / DRAFT 均可归档，ARCHIVED 为终态。
     */
    public void archive() {
        if (!status.canArchive()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_STATE_CONFLICT, "已归档课程为终态");
        }
        status = CourseStatus.ARCHIVED;
    }

    /**
     * 规范化课程编码：去空格 + 大写。
     *
     * @param raw 原始编码
     * @return 规范化编码
     */
    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_CODE_CONFLICT, "课程编码不能为空");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
