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
    @Size(max = 64, message = "课程编码最长 64 字符")
    private String courseCode;

    /** 课程名称 */
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 128, message = "课程名称最长 128 字符")
    private String title;

    /** 分类 */
    @Size(max = 64, message = "分类最长 64 字符")
    private String category;

    /** 简介 */
    @Size(max = 1000, message = "简介最长 1000 字符")
    private String description;

    /** 讲师 */
    @Size(max = 64, message = "讲师最长 64 字符")
    private String lecturer;
}
