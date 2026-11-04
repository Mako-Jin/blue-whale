package com.bluewhale.workflow.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程实例分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessInstanceQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 实例标题（模糊匹配） */
    private String title;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销（精确匹配） */
    private Integer status;

    /** 流程定义编码（精确匹配） */
    private String definitionCode;

    /** 发起人账号（精确匹配；用于「我发起」） */
    private String initiator;
}
