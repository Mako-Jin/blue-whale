package com.bluewhale.pdm.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmProductQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 产品编码（模糊匹配） */
    private String code;

    /** 产品名称（模糊匹配） */
    private String name;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
