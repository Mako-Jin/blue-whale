package com.bluewhale.lms.course.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程保存命令（新增 / 编辑）。
 *
 * @author BlueWhale
 */
@Data
public class CourseSaveCommand {

    /** 课程编码（新增必填，编辑不可变更） */
    @Size(max = 64, message = "{validation.lms.course.course-code-size}")
    private String courseCode;

    /** 课程名称 */
    @NotBlank(message = "{validation.lms.course.title-required}")
    @Size(max = 128, message = "{validation.lms.course.title-size}")
    private String title;

    /** 分类 */
    @Size(max = 64, message = "{validation.lms.course.category-size}")
    private String category;

    /** 简介 */
    @Size(max = 1000, message = "{validation.lms.course.description-size}")
    private String description;

    /** 讲师 */
    @Size(max = 64, message = "{validation.lms.course.lecturer-size}")
    private String lecturer;
}
