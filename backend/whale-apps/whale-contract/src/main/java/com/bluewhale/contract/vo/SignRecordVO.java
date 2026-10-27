package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 签署记录视图对象。
 *
 * @author BlueWhale
 */
@Data
public class SignRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private String id;

    /** 合同ID */
    private String contractId;

    /** 签署任务ID */
    private String signTaskId;

    /** 签署人用户ID */
    private String signerUserId;

    /** 印章ID */
    private String sealId;

    /** 证书序列号 */
    private String certSerial;

    /** 签署时间 */
    private LocalDateTime signedAt;

    /** 盖章前文件 SHA-256 */
    private String originalSha256;

    /** 签章件 SHA-256 */
    private String signedSha256;

    /** 签名算法 */
    private String algorithm;

    /** 证据编号 */
    private String evidenceNo;

    /** 链式哈希 */
    private String chainHash;

    /** 签署客户端IP */
    private String clientIp;

    /** 实名认证状态 */
    private String realNameStatus;

    /** 实名认证方式 */
    private String realNameMethod;

    /** 实名认证证据编号 */
    private String realNameEvidenceNo;

    /** 意愿认证方式 */
    private String willingnessMethod;

    /** 意愿认证证据编号 */
    private String willingnessEvidenceNo;

    /** 意愿确认时间 */
    private LocalDateTime willingnessAt;
}
