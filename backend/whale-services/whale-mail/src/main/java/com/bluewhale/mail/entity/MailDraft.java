package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邮件草稿实体，对应表 {@code mail_draft}。
 *
 * <p>草稿独立成表而非塞进 {@code mail_message}：草稿没有 IMAP UID，也不参与
 * 未读数与索引重建，混在一起会让同步逻辑充满分支。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_draft")
public class MailDraft extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属邮箱账号ID */
    private Long accountId;

    /** 客户端草稿键 */
    private String draftKey;

    /** 主题 */
    private String subject;

    /** 收件人 */
    private String toAddrs;

    /** 抄送 */
    private String ccAddrs;

    /** 密送 */
    private String bccAddrs;

    /** HTML 正文 */
    private String bodyHtml;

    /** 纯文本正文 */
    private String bodyText;

    /** 附件草稿（JSON 数组） */
    private String attachmentsJson;
}
