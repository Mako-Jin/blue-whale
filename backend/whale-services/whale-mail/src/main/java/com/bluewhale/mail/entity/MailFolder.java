package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邮箱文件夹实体，对应表 {@code mail_folder}。
 *
 * <p>{@code imapName} 保留 IMAP 原始名称（含层级分隔符），{@code folderCode} 是平台对外
 * 稳定编码；两者不可互换——IMAP 名称随服务商变化，编码是业务契约。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_folder")
public class MailFolder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属邮箱账号ID */
    private Long accountId;

    /** 文件夹编码：INBOX/SENT/DRAFT/TRASH/JUNK/CUSTOM */
    private String folderCode;

    /** 展示名称 */
    private String name;

    /** IMAP 原始文件夹名 */
    private String imapName;

    /** 类型：SYSTEM 系统 / CUSTOM 自定义 */
    private String folderType;

    /** 未读数量 */
    private Integer unreadCount;

    /** 邮件总数 */
    private Integer totalCount;

    /** IMAP UIDVALIDITY（按文件夹维护） */
    private Long uidValidity;

    /** 该文件夹已同步到的最大 UID */
    private Long lastUid;

    /** 排序号 */
    private Integer sortNo;
}
