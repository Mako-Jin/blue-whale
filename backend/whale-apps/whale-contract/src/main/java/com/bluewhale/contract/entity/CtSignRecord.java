package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 签署记录实体，对应表 {@code ct_sign_record}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_sign_record")
public class CtSignRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 签署任务ID */
    private Long signTaskId;

    /** 签署人用户ID */
    private Long signerUserId;

    /** 使用印章ID */
    private Long sealId;

    /** 签名证书序列号 */
    private String certSerial;

    /** 签署时间 */
    private LocalDateTime signedAt;

    /** 盖章前文件 SHA-256 */
    private String originalSha256;

    /** 签章件 SHA-256 */
    private String signedSha256;

    /** 签名算法：SHA256withRSA/NONE */
    private String algorithm;

    /** 证据编号 */
    private String evidenceNo;

    /** 链式哈希 */
    private String chainHash;

    /** 签署客户端IP */
    private String clientIp;

    /** 实名认证状态：VERIFIED/UNVERIFIED/PENDING */
    private String realNameStatus;

    /** 实名认证方式：LOGIN_SESSION/ID_CARD_OCR/FACE/VIDEO/SMS */
    private String realNameMethod;

    /** 实名认证证据编号（供应商回执/本地会话凭据） */
    private String realNameEvidenceNo;

    /** 意愿认证方式：SIGN_CONFIRM/SMS/FACE/VIDEO */
    private String willingnessMethod;

    /** 意愿认证证据编号 */
    private String willingnessEvidenceNo;

    /** 意愿确认时间 */
    private LocalDateTime willingnessAt;
}
