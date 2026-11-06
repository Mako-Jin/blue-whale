package com.bluewhale.lms.course.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程实体，对应表 {@code lr_course}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_course")
public class CourseEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
    private String status;
}
