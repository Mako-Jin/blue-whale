package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 收信规则实体，对应表 {@code mail_rule}。
 *
 * <p>规则只改本平台的归类，不动服务器上的文件夹：一次归类若要在服务器上生效，
 * 需要 IMAP MOVE，而移动会更换 UID、影响其它客户端与服务器侧配额统计，
 * 失败时的回滚也比本地改一个字段复杂得多。取舍见 docs/ops/mail-service-ops.md §十四。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_rule")
public class MailRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属邮箱账号ID */
    private Long accountId;

    /** 规则名称 */
    private String name;

    /** 优先级（小的先匹配，命中即停） */
    private Integer sortNo;

    /** 启用：1 是 / 0 否 */
    private Integer enabled;

    /** 发件人包含（逗号分隔多值，任一命中） */
    private String matchFrom;

    /** 主题包含（逗号分隔多值，任一命中） */
    private String matchSubject;

    /** 收件人包含（逗号分隔多值，任一命中） */
    private String matchTo;

    /** 仅含附件邮件：1 是 / 0 不限制 */
    private Integer onlyWithAttachment;

    /** 归入文件夹ID（空表示不移动） */
    private Long actionFolderId;

    /** 标记已读：1 是 / 0 否 */
    private Integer actionMarkSeen;

    /** 加星标：1 是 / 0 否 */
    private Integer actionFlagged;

    /** 累计命中封数 */
    private Long hitCount;

    /** 最近命中时间 */
    private LocalDateTime lastHitTime;
}
