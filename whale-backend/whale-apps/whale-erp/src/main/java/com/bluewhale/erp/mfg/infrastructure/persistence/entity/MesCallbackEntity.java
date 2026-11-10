package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MES 回调幂等流水，对应表 {@code erp_mfg_mes_callback}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mes_callback")
public class MesCallbackEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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
