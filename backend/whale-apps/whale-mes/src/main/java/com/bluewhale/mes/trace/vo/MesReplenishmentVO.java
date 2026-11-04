package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线边叫料补货请求视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesReplenishmentVO {

    /** 请求 ID */
    private Long id;

    /** 请求号 */
    private String requestNo;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    private String lineCode;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 叫料数量 */
    private BigDecimal quantity;

    /** 计量单位 */
    private String uom;

    /** 状态 */
    private String status;

    /** 叫料原因 */
    private String reason;

    /** 叫料人 */
    private String requestedBy;

    /** 叫料时间 */
    private LocalDateTime requestedAt;

    /** ERP 调拨单号 */
    private String erpTransferNo;

    /** 备注 */
    private String remark;
}
