package com.bluewhale.lms.course.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程章节实体，对应表 {@code lr_course_chapter}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_course_chapter")
public class CourseChapterEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属课程 ID */
    private Long courseId;

    /** 章节标题 */
    private String title;

    /** 排序号 */
    private Integer sortNo;

    /** 简介备注 */
    private String remark;
}
