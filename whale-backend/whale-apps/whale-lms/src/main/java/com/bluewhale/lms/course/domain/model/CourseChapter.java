package com.bluewhale.lms.course.domain.model;

import lombok.Data;

/**
 * 课程章节（课程聚合内实体）。
 *
 * @author BlueWhale
 */
@Data
public class CourseChapter {

    /** 主键 */
    private Long id;

    /** 所属课程 ID */
    private Long courseId;

    /** 章节标题 */
    private String title;

    /** 排序号（升序展示） */
    private Integer sortNo;

    /** 简介备注 */
    private String remark;
}
