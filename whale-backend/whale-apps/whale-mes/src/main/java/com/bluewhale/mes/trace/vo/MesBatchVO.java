package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料批次视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesBatchVO {

    /** 批次 ID */
    private Long id;

    /** 批次号 */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 批次类型 */
    private String batchType;

    /** 来源 */
    private String sourceType;

    /** 来源工单 ID */
    private Long workOrderId;

    /** 来源工单编码 */
    private String workOrderCode;

    /** 供应商编码 */
    private String supplierCode;

    /** 批次初始数量 */
    private BigDecimal quantity;

    /** 已消耗数量 */
    private BigDecimal consumedQuantity;

    /** 剩余可消耗数量 */
    private BigDecimal remainingQuantity;

    /** 计量单位 */
    private String uom;

    /** 生产/到货日期 */
    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
