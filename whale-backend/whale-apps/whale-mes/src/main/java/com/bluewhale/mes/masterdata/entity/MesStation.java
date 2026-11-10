package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 工位主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_station")
public class MesStation extends MesMasterDataEntity {

    /** 所属产线 ID */
    private Long lineId;

    /** 所属工作中心 ID */
    private Long workCenterId;
}
