package com.bluewhale.workflow.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 流程名称（模糊匹配） */
    private String name;

    /** 流程编码（精确匹配） */
    private String code;

    /** 状态：1 正常 / 0 禁用（精确匹配） */
    private Integer status;
}
