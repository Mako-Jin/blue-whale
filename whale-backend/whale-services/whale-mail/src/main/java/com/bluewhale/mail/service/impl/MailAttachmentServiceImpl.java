package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mail.client.MailFileClient;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailAttachment;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailAttachmentMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailAttachmentService;
import com.bluewhale.mail.support.FetchedAttachment;
import com.bluewhale.mail.support.MailImapSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件附件服务实现。
 *
 * <p>两个刻意的取舍：</p>
 * <ol>
 *   <li><b>按需登记</b>：只有用户打开详情（或下载）时才连 IMAP 取附件并写文件服务。
 *       同步阶段下载全部附件会让「首同步」变成一次全量下载，存储与带宽都不可控。</li>
 *   <li><b>超限不静默丢弃</b>：超过单文件/单封上限的附件不入库字节，但仍写一行元信息，
 *       下载时给出明确提示。用户看到名字却下不动，比「列表里根本没有」更可解释。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailAttachmentServiceImpl implements MailAttachmentService {

    /** 文件名最大长度（与 mail_attachment.file_name 一致） */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    /** MIME 类型最大长度（与 mail_attachment.content_type 一致） */
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;

    /** 内联资源标记：是 */
    private static final int INLINE_YES = 1;

    /** 内联资源标记：否 */
    private static final int INLINE_NO = 0;

    private final MailAttachmentMapper attachmentMapper;

    private final MailMessageMapper messageMapper;

    private final MailAccountService accountService;

    private final MailImapSupport imapSupport;

    private final MailFileClient fileClient;

    private final MailProperties properties;

    @Override
    public List<MailAttachment> ensureIngested(MailMessage message, MailAccount account, MailFolder folder) {
        List<MailAttachment> existing = listByMessage(message.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        if (message.getHasAttachment() == null || message.getHasAttachment() != 1) {
            return List.of();
        }
        List<FetchedAttachment> fetched;
        try {
            fetched = imapSupport.fetchAttachments(account, folder, message.getUid(),
                    properties.getFile().getMaxAttachmentBytes());
        } catch (BizException e) {
            // 登记失败不该让详情整页 500：正文与头部仍可读，附件下次打开再试
            log.warn("附件登记失败: messageId={}, reason={}", message.getId(), e.getMessage());
            return List.of();
        }
        if (fetched.isEmpty()) {
            return List.of();
        }
        long totalLimit = properties.getFile().getMaxTotalAttachmentBytes();
        long accumulated = 0L;
        List<MailAttachment> saved = new ArrayList<>(fetched.size());
        for (FetchedAttachment item : fetched) {
            Long fileId = null;
            long size = item.effectiveSize();
            if (item.hasContent() && (totalLimit <= 0 || accumulated + size <= totalLimit)) {
                try {
                    fileId = fileClient.upload(item.content(), safeFileName(item.fileName()));
                    accumulated += size;
                } catch (BizException e) {
                    log.warn("附件上传失败，降级为仅登记元信息: messageId={}, fileName={}, reason={}",
                            message.getId(), item.fileName(), e.getMessage());
                }
            } else if (item.hasContent()) {
                log.warn("附件超出单封总量上限，仅登记元信息: messageId={}, fileName={}", message.getId(),
                        item.fileName());
            }
            saved.add(insert(message.getId(), fileId, item, size));
        }
        message.setHasAttachment(1);
        messageMapper.updateById(message);
        return saved;
    }

    @Override
    public AttachmentContent download(Long attachmentId, Long userId) {
        MailAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        MailMessage message = messageMapper.selectById(attachment.getMessageId());
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件所属邮件不存在");
        }
        accountService.requireOwned(message.getAccountId(), userId);
        if (attachment.getFileId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "该附件超出单封容量上限，未缓存内容，无法下载");
        }
        byte[] content = fileClient.download(attachment.getFileId());
        return new AttachmentContent(safeFileName(attachment.getFileName()),
                attachment.getContentType(), content);
    }

    /**
     * 查询某封邮件已登记的附件。
     *
     * @param messageId 邮件ID
     * @return 附件列表
     */
    private List<MailAttachment> listByMessage(Long messageId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                .eq(MailAttachment::getMessageId, messageId)
                .orderByAsc(MailAttachment::getId));
    }

    /**
     * 写入一行附件元信息。
     *
     * @param messageId 邮件ID
     * @param fileId 文件ID（未缓存时为 null）
     * @param item IMAP 快照
     * @param size 字节数
     * @return 已入库实体
     */
    private MailAttachment insert(Long messageId, Long fileId, FetchedAttachment item, long size) {
        MailAttachment attachment = new MailAttachment();
        attachment.setMessageId(messageId);
        attachment.setFileId(fileId);
        attachment.setFileName(safeFileName(item.fileName()));
        attachment.setContentType(normalizeContentType(item.contentType()));
        attachment.setSizeBytes(size);
        attachment.setInlineFlag(item.inline() ? INLINE_YES : INLINE_NO);
        attachment.setContentId(StringUtils.hasText(item.contentId()) ? item.contentId() : null);
        attachmentMapper.insert(attachment);
        return attachment;
    }

    /**
     * 归一文件名：空名用占位名，超长截断。
     *
     * @param fileName 原始文件名
     * @return 可用文件名
     */
    private static String safeFileName(String fileName) {
        String value = StringUtils.hasText(fileName) ? fileName.trim() : "附件";
        return value.length() <= MAX_FILE_NAME_LENGTH ? value : value.substring(0, MAX_FILE_NAME_LENGTH);
    }

    /**
     * 归一 MIME 类型：去掉 {@code ;charset=...} 等参数并截断。
     *
     * <p>带参数的 Content-Type 存进库再传给文件服务，会被当成非法 MIME 拒绝。</p>
     *
     * @param contentType 原始类型
     * @return 归一后的类型
     */
    private static String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        String value = (semicolon > 0 ? contentType.substring(0, semicolon) : contentType).trim();
        return value.length() <= MAX_CONTENT_TYPE_LENGTH ? value : value.substring(0, MAX_CONTENT_TYPE_LENGTH);
    }
}
