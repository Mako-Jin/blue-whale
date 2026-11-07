package com.bluewhale.erp.mfg.application.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * MES 完工回传报文（payload 部分）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MesCompletionCallback {

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** MES 工单 ID */
    private Long workOrderId;

    /** MES 工单编码 */
    private String workOrderCode;

    /** 物料编码 */
    private String itemCode;

    /** 累计完工数量 */
    private BigDecimal completedQuantity;

    /** 计量单位 */
    private String uom;
}
