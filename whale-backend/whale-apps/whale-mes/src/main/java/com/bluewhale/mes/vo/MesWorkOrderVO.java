package com.bluewhale.mes.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesWorkOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 工单编码 */
    private String code;

    /** 工单名称 */
    private String name;

    /** 状态：RECEIVED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/CLOSED/CANCELLED */
    private String status;

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 计量单位 */
    private String uom;

    /** 工艺路线版本快照 */
    private String routingVersion;

    /** 优先级：NORMAL / URGENT */
    private String priority;

    /** 计划开工时间 */
    private LocalDateTime plannedStart;

    /** 计划完工时间 */
    private LocalDateTime plannedEnd;

    /** 乐观锁版本号 */
    private Integer version;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
