package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产用料调整记录（退料 RETURN / 报废 SCRAP）。
 *
 * @author BlueWhale
 */
@Data
public class MaterialAdjustment {

    /** 调整单号 */
    private String docNo;

    /** 调整类型：RETURN / SCRAP */
    private String adjType;

    /** 生产订单 ID */
    private Long orderId;

    /** 用料行号 */
    private Integer lineNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 调整数量 */
    private BigDecimal quantity;

    /** 调整金额 */
    private BigDecimal amount;

    /** 原因 */
    private String reason;

    /** 操作人 ID */
    private Long operatorId;

    /** 发生时间 */
    private LocalDateTime occurredAt;
}
