package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 验签结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class VerifyResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private String contractId;

    /** 文件哈希是否与签署记录一致 */
    private Boolean hashMatch;

    /** 链式哈希是否连续 */
    private Boolean chainValid;

    /** 签名是否有效（视觉章 + 哈希 + 可选 CMS） */
    private Boolean signatureValid;

    /** 签名算法 */
    private String algorithm;

    /** 签章件文件ID */
    private String signedFileId;

    /** 数据库记录的 SHA-256 */
    private String expectedSha256;

    /** 文件实际 SHA-256 */
    private String actualSha256;

    /** 明细说明 */
    private List<String> details;
}
