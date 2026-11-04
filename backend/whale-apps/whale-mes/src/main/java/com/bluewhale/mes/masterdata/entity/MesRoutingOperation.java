package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工艺路线工序快照实体，对应表 {@code mes_md_routing_operation}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_routing_operation")
public class MesRoutingOperation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工艺路线快照 ID */
    private Long routingId;

    /** 工序顺序 */
    private Integer seqNo;

    /** 工序编码 */
    private String operationCode;

    /** 工序名称 */
    private String operationName;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 标准工时（分钟） */
    private BigDecimal standardMinutes;

    /** 备注 */
    private String remark;
}
