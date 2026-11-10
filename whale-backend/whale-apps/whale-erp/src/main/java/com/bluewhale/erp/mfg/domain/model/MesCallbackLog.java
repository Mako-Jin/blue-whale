package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MES 回调处理流水。
 *
 * @author BlueWhale
 */
@Data
public class MesCallbackLog {

    /** 生产订单 ID */
    private Long orderId;

    /** ERP 生产订单号 */
    private String erpOrderNo;

    /** 回调类型：COMPLETION/PROGRESS */
    private String callbackType;

    /** MES 事件幂等键 */
    private String sourceEventId;

    /** 处理结果：APPLIED/REJECTED/DUPLICATE */
    private String status;

    /** 原始事件报文 */
    private String payload;

    /** 失败摘要 */
    private String error;

    /** 接收时间 */
    private LocalDateTime receivedAt;
}
