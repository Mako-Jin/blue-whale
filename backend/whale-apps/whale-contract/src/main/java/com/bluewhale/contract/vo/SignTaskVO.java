package com.bluewhale.contract.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 签署任务视图对象。
 *
 * @author BlueWhale
 */
@Data
public class SignTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String id;

    /** 合同ID */
    private String contractId;

    /** 合同编号（待签列表返回） */
    private String contractCode;

    /** 合同标题（待签列表返回） */
    private String contractTitle;

    /** 合同金额（待签列表返回） */
    private java.math.BigDecimal contractAmount;

    /** 合同类别（待签列表返回） */
    private String contractCategory;

    /** 版本ID */
    private String versionId;

    /** 签署顺序号 */
    private Integer seqNo;

    /** 签署人用户ID */
    private String signerUserId;

    /** 签署方 */
    private String signerParty;

    /** 签署人姓名 */
    private String signerName;

    /** 签署渠道 */
    private String provider;

    /** 状态 */
    private String status;

    /** 签署时间 */
    private LocalDateTime signedAt;

    /** 签章件文件ID */
    private String signedFileId;

    /** 拒签原因 */
    private String refuseReason;

    /** 创建时间 */
    private LocalDateTime createTime;
}
