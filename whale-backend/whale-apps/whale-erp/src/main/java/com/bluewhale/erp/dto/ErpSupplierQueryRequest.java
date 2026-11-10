package com.bluewhale.erp.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ErpSupplierQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 供应商编码（模糊匹配） */
    private String code;

    /** 供应商名称（模糊匹配） */
    private String name;

    /** 供应商分类（精确匹配） */
    private String category;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
