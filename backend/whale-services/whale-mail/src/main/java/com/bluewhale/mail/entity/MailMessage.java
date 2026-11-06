package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 邮件索引实体，对应表 {@code mail_message}。
 *
 * <p>列表查询只依赖本表的元数据列，正文列 {@code bodyText}/{@code bodyHtml} 为懒加载缓存：
 * 未命中时由 IMAP 按 UID 回源后回填，避免同步阶段拉取全量正文拖垮存储与带宽。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_message")
public class MailMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属邮箱账号ID */
    private Long accountId;

    /** 所属文件夹ID */
    private Long folderId;

    /** IMAP UID（文件夹内唯一） */
    private Long uid;

    /** 同步时的 UIDVALIDITY 快照 */
    private Long uidValidity;

    /** RFC822 Message-ID */
    private String messageId;

    /** 主题 */
    private String subject;

    /** 发件人地址 */
    private String fromAddr;

    /** 发件人显示名 */
    private String fromName;

    /** 收件人（多值逗号分隔） */
    private String toAddrs;

    /** 抄送 */
    private String ccAddrs;

    /** 密送 */
    private String bccAddrs;

    /** 回复地址 */
    private String replyTo;

    /** 发信时间 */
    private LocalDateTime sentTime;

    /** 收信时间 */
    private LocalDateTime receivedTime;

    /** 邮件大小（字节） */
    private Long sizeBytes;

    /** 是否含附件：1 是 / 0 否 */
    private Integer hasAttachment;

    /** 已读：1 是 / 0 否 */
    private Integer seen;

    /** 星标：1 是 / 0 否 */
    private Integer flagged;

    /** 已回复：1 是 / 0 否 */
    private Integer answered;

    /** 已转发：1 是 / 0 否 */
    private Integer forwarded;

    /** 优先级：1 最高 / 3 普通 / 5 最低 */
    private Integer priority;

    /** 纯文本摘要 */
    private String preview;

    /** 纯文本正文 */
    private String bodyText;

    /** HTML 正文（渲染前必须净化） */
    private String bodyHtml;

    /** 会话键 */
    private String threadKey;
}
