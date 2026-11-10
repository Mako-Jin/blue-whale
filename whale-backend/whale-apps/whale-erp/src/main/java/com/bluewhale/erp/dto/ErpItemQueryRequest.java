package com.bluewhale.erp.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物料分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpItemQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 物料编码（模糊匹配） */
    private String code;

    /** 物料名称（模糊匹配） */
    private String name;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
