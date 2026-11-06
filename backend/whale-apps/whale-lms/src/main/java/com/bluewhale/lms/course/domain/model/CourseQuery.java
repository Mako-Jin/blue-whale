package com.bluewhale.lms.course.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（编码 / 名称） */
    private String keyword;

    /** 分类 */
    private String category;

    /** 状态（DRAFT / PUBLISHED / ARCHIVED，空为全部） */
    private String status;
}
