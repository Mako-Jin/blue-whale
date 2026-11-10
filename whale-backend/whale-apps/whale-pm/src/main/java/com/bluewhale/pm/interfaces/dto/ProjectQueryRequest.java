package com.bluewhale.pm.interfaces.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（项目名称或项目键模糊匹配） */
    private String keyword;

    /** 项目状态：ACTIVE / ARCHIVED */
    private String status;

    /** 项目类型：SCRUM / KANBAN */
    private String projectType;
}
