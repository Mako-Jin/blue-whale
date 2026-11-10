package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 邮箱账号实体，对应表 {@code mail_account}。
 *
 * <p>一个平台用户可绑定一个邮箱地址；协议端点（IMAP/SMTP）按账号维度独立配置，
 * 便于同时对接自建 Mailcow 与外部企业邮箱。</p>
 *
 * <p><b>凭据二选一</b>：{@code secretRef} 指向部署侧注入的配置键（外部托管邮箱），
 * {@code secretCipher} 存平台代开时随机生成的口令密文（见 {@code MailSecretCipher}）。
 * 两者都不出现明文，且严禁在 VO 或日志中回显。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_account")
public class MailAccount extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 平台用户ID（whale-auth sys_user.id） */
    private Long userId;

    /** 邮箱地址（租户内唯一） */
    private String email;

    /** 发件显示名 */
    private String displayName;

    /** 邮箱域名 */
    private String domain;

    /** 账号类型：PERSONAL 个人 / SHARED 共享 */
    private String accountType;

    /** 开通方式：MANUAL 手工绑定 / AUTO 平台代开 */
    private String provisionSource;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 是否参与增量同步：1 是 / 0 否 */
    private Integer syncEnabled;

    /** 凭据引用（Nacos 配置键或环境变量名；密码本身不入库） */
    private String secretRef;

    /** 平台代开的口令密文（{@code ENC(...)}；与 {@code secretRef} 二选一） */
    private String secretCipher;

    /** IMAP 主机 */
    private String imapHost;

    /** IMAP 端口 */
    private Integer imapPort;

    /** IMAP 是否 SSL：1 是 / 0 否 */
    private Integer imapSsl;

    /** SMTP 主机 */
    private String smtpHost;

    /** SMTP 端口 */
    private Integer smtpPort;

    /** SMTP 是否 SSL：1 是（465）/ 0 STARTTLS（587） */
    private Integer smtpSsl;

    /** 配额（字节，0 表示未知） */
    private Long quotaBytes;

    /** 已用（字节） */
    private Long usedBytes;

    /** 最近同步时间 */
    private LocalDateTime lastSyncTime;
}
