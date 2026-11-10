package com.bluewhale.mes.masterdata.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 车间主数据查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesMasterDataQueryRequest extends PageQuery {

    /** 编码（模糊） */
    private String code;

    /** 名称（模糊） */
    private String name;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 车间 ID */
    private Long workshopId;

    /** 产线 ID */
    private Long lineId;

    /** 工作中心 ID */
    private Long workCenterId;
}
