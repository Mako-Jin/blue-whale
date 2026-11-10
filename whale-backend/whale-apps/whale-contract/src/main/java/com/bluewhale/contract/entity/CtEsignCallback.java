package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电子签章回调实体，对应表 {@code ct_esign_callback}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_esign_callback")
public class CtEsignCallback extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 签署渠道：LOCAL/ESIGNBAO/FADADA/SSQ */
    private String provider;

    /** 三方事件ID（幂等键） */
    private String providerEventId;

    /** 合同ID */
    private Long contractId;

    /** 签署任务ID */
    private Long signTaskId;

    /** 回调原文 SHA-256 */
    private String payloadHash;

    /** 回调原文（JSON） */
    private String payload;

    /** 状态：RECEIVED/PROCESSED/FAILED */
    private String status;

    /** 处理失败重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMsg;
}
