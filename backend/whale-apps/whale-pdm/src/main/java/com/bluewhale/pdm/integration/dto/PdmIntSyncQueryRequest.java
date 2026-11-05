package com.bluewhale.pdm.integration.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 集成同步日志查询
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PdmIntSyncQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 通道：CAD/ERP/MES/OA */
    private String channel;

    /** 动作 */
    private String action;

    /** 状态：SUCCESS/FAILED */
    private String status;

    /** 业务编号 */
    private String bizNo;

    /** 业务类型：PART/DOCUMENT/BOM/ROUTE/CHANGE */
    private String bizType;
}
