package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES 工作中心主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_work_center")
public class MesWorkCenter extends MesMasterDataEntity {

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属产线 ID */
    private Long lineId;

    /** 类型：MACHINE/LINE/CELL */
    private String centerType;

    /** 每班产能 */
    private BigDecimal capacityPerShift;
}
