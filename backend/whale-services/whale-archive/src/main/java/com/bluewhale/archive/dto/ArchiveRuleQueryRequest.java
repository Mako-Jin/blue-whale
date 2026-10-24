package com.bluewhale.archive.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 归档规则分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveRuleQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 流程定义编码（模糊匹配） */
    private String definitionCode;

    /** 归档门类ID（精确匹配） */
    private Long categoryId;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;
}
