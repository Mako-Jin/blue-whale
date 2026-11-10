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

    /** ERP 生产订单号（模糊匹配） */
    private String erpOrderNo;

    /** 物料编码（模糊匹配） */
    private String itemCode;

    /** 状态：RECEIVED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/CLOSED/CANCELLED */
    private String status;

    /** 优先级：NORMAL / URGENT */
    private String priority;
}
