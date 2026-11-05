package com.bluewhale.pdm.integration.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 集成对账结果查询
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmIntReconcileQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 通道：ERP/MES/OA */
    private String channel;

    /** 业务类型：BOM/ROUTE/CHANGE */
    private String bizType;

    /** 业务编号 */
    private String bizNo;

    /** 对账结论：MATCH/DIFF/MISSING */
    private String matchStatus;
}
