package com.bluewhale.workflow.dto;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.Size;
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

    /** 流程名称（模糊匹配，最长 128） */
    @Size(max = 128, message = "流程名称长度不能超过 128")
    private String name;

    /** 流程编码（精确匹配，最长 64） */
    @Size(max = 64, message = "流程编码长度不能超过 64")
    private String code;

    /** 状态：1 正常 / 0 禁用（精确匹配） */
    private Integer status;
}
