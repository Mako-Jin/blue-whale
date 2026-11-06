package com.bluewhale.lms.course.application.view;

import com.bluewhale.lms.course.domain.model.Course;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程视图（列表 / 详情头）。
 *
 * @author BlueWhale
 */
@Data
public class CourseView {

    /** 主键 */
    private Long id;

    /** 课程编码 */
    private String courseCode;

    /** 课程名称 */
    private String title;

    /** 分类 */
    private String category;

    /** 简介 */
    private String description;

    /** 讲师 */
    private String lecturer;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从领域模型转换。
     *
     * @param course 课程
     * @return 视图
     */
    public static CourseView from(Course course) {
        CourseView view = new CourseView();
        view.setId(course.getId());
        view.setCourseCode(course.getCourseCode());
        view.setTitle(course.getTitle());
        view.setCategory(course.getCategory());
        view.setDescription(course.getDescription());
        view.setLecturer(course.getLecturer());
        view.setStatus(course.getStatus().name());
        view.setCreateTime(course.getCreateTime());
        return view;
    }
}
