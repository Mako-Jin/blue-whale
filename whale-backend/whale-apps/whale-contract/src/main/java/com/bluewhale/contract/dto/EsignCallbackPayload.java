package com.bluewhale.contract.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 三方电子签章回调报文。
 *
 * <p>{@code providerEventId} 为幂等键；签署任务可通过平台任务ID {@code signTaskId}
 * 或三方任务号 {@code providerTaskId} 定位。签章件可选传 {@code signedFileId/signedSha256}，
 * 未传时由平台按渠道下载或回调原文兜底计算哈希。</p>
 *
 * @author BlueWhale
 */
@Data
public class EsignCallbackPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 三方事件ID（必填，幂等键） */
    private String providerEventId;

    /** 动作：SIGN 签署完成 / REFUSE 拒签 */
    private String action;

    /** 平台签署任务ID（可选，优先使用） */
    private String signTaskId;

    /** 三方任务号（与 signTaskId 二选一） */
    private String providerTaskId;

    /** 证据编号（可选，缺省由平台生成） */
    private String evidenceNo;

    /** 证书序列号（可选） */
    private String certSerial;

    /** 签署时间（ISO-8601，如 2026-10-26T10:00:00） */
    private String signedAt;

    /** 已上传的签章件文件ID（可选） */
    private String signedFileId;

    /** 签章件 SHA-256（可选，三方给出时用于校验） */
    private String signedSha256;

    /** 拒签原因（REFUSE 时必填） */
    private String refuseReason;

    /** 实名认证状态：VERIFIED/UNVERIFIED/PENDING（可选，缺省 UNVERIFIED） */
    private String realNameStatus;

    /** 实名认证方式：ID_CARD_OCR/FACE/VIDEO/SMS 等（可选） */
    private String realNameMethod;

    /** 实名认证证据编号（可选） */
    private String realNameEvidenceNo;

    /** 意愿认证方式：SMS/FACE/VIDEO/SIGN_CONFIRM 等（可选） */
    private String willingnessMethod;

    /** 意愿认证证据编号（可选） */
    private String willingnessEvidenceNo;

    /** 意愿确认时间（ISO-8601，可选；缺省取签署时间） */
    private String willingnessAt;
}
