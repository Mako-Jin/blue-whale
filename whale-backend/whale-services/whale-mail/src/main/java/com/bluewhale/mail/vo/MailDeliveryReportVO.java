package com.bluewhale.mail.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退信回执视图。
 *
 * @author BlueWhale
 */
@Data
public class MailDeliveryReportVO {

    /** 回执ID */
    private Long id;

    /** 退信通知邮件ID */
    private Long notifyMessageId;

    /** 被退回的原邮件ID（未关联时为空） */
    private Long originalMessageId;

    /** 被退回原邮件的 Message-ID */
    private String originalRfcId;

    /** 被退回的收件人地址 */
    private String recipient;

    /** DSN Action */
    private String action;

    /** 增强状态码 */
    private String statusCode;

    /** 远端诊断信息 */
    private String diagnostic;

    /** HARD / SOFT / UNKNOWN */
    private String bounceType;

    /** Reporting-MTA */
    private String reportingMta;

    /** Remote-MTA */
    private String remoteMta;

    /** 入库时间 */
    private LocalDateTime createTime;
}
