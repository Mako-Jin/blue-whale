package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 退信回执实体，对应表 {@code mail_delivery_report}。
 *
 * <p>一行是「一封退信通知里针对一个收件人的一条结果」。同一封原邮件群发给多人时，
 * 一个收件人被退回就会产生一行，其余收件人正常投递不记录——只关心失败的那部分。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_delivery_report")
public class MailDeliveryReport extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属邮箱账号ID */
    private Long accountId;

    /** 退信通知邮件ID（mail_message.id，即 DSN 本身） */
    private Long notifyMessageId;

    /** 被退回的原邮件ID（按 Message-ID 关联，未命中为空） */
    private Long originalMessageId;

    /** 被退回原邮件的 RFC822 Message-ID */
    private String originalRfcId;

    /** 被退回的收件人地址 */
    private String recipient;

    /** DSN Action：failed / delayed / delivered */
    private String action;

    /** RFC 3463 增强状态码，如 5.1.1 */
    private String statusCode;

    /** 远端返回的诊断信息（SMTP 应答原文） */
    private String diagnostic;

    /** 退信类型：HARD 永久 / SOFT 临时 / UNKNOWN 无法判定 */
    private String bounceType;

    /** Reporting-MTA（哪一跳退回的） */
    private String reportingMta;

    /** Remote-MTA（对端服务器） */
    private String remoteMta;
}
