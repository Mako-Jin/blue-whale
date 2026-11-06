package com.bluewhale.mail.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 邮件分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MailMessageQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 邮箱账号ID；为空时取当前用户默认账号 */
    private Long accountId;

    /** 文件夹ID（与 folderCode 二选一，优先本字段） */
    private Long folderId;

    /** 文件夹编码：INBOX/SENT/DRAFT/TRASH/JUNK/CUSTOM */
    private String folderCode;

    /** 关键字（主题、发件人、摘要、正文全文检索） */
    private String keyword;

    /**
     * 检索范围：{@code FOLDER} 当前文件夹（默认）/ {@code ALL} 全部文件夹。
     *
     * <p>只在带关键字时生效。默认按当前文件夹更符合直觉——用户在「已发送」里搜，
     * 不该被收件箱的结果淹没；但「我记得有这封邮件，忘了在哪个目录」也确实常见。</p>
     */
    private String scope;

    /** 已读状态：1 已读 / 0 未读；为空不筛选 */
    private Integer seen;

    /** 是否有附件：1 有 / 0 无；为空不筛选 */
    private Integer hasAttachment;

    /** 发件人地址（精确匹配） */
    private String fromAddr;

    /** 发信时间起 */
    private LocalDateTime startTime;

    /** 发信时间止 */
    private LocalDateTime endTime;
}
