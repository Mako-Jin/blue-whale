package com.bluewhale.mes.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生产工单分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesWorkOrderQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 工单编码（模糊匹配） */
    private String code;

    /** 工单名称（模糊匹配） */
    private String name;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
