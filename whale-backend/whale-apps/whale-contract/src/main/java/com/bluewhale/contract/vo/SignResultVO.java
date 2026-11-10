package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 本地签署结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class SignResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 签署任务ID */
    private String taskId;

    /** 签署记录ID */
    private String recordId;

    /** 证据编号 */
    private String evidenceNo;

    /** 盖章前文件 SHA-256 */
    private String originalSha256;

    /** 签章件 SHA-256 */
    private String signedSha256;

    /** 签名算法 */
    private String algorithm;

    /** 签章件文件ID */
    private String signedFileId;

    /** 实际盖章页数（骑缝章等于 PDF 总页数） */
    private Integer stampedPages;

    /** 任务状态 */
    private String taskStatus;

    /** 合同状态 */
    private String contractStatus;
}
