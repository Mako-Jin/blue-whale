package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES 工序主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_operation")
public class MesOperation extends MesMasterDataEntity {

    /** 工序类型：PROCESS/INSPECT/PACK */
    private String operationType;

    /** 标准工时（分钟） */
    private BigDecimal standardMinutes;
}
