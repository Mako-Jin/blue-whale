package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mail.client.MailFileClient;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailAttachmentRequest;
import com.bluewhale.mail.dto.MailSendRequest;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailAttachment;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailAttachmentMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.service.MailSendService;
import com.bluewhale.mail.support.MailAddressUtils;
import com.bluewhale.mail.support.MailCredentialResolver;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * 发信服务实现。
 *
 * <p>发信是「先外发、后建索引」：SMTP 成功才写已发送副本，避免发送失败却在列表里留下幽灵邮件。
 * SMTP 调用不包事务——长事务里做网络 IO 会占死连接池。</p>
 *
 * <p>M1 附件：附件字节由 {@code whale-file} 按 {@code fileId} 取流后组装 multipart，
 * 本服务只落附件元信息；请求里带的每个附件都会在发送成功后登记一行，
 * 保证「已发送」列表点开能看到自己发过的附件。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendServiceImpl implements MailSendService {

    /** SMTP 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** SMTP 读超时（毫秒） */
    private static final int READ_TIMEOUT_MS = 30_000;

    /** 已发送文件夹编码 */
    private static final String FOLDER_SENT = "SENT";

    private final MailAccountService accountService;

    private final MailFolderService folderService;

    private final MailMessageMapper messageMapper;

    private final MailCredentialResolver credentialResolver;

    private final MailProperties properties;

    private final MailFileClient fileClient;

    private final MailAttachmentMapper attachmentMapper;

    @Override
    public void send(MailSendRequest request, Long userId) {
        MailAccount account = accountService.requireOwned(request.getAccountId(), userId);
        List<SendAttachment> attachments = loadAttachments(request.getAttachments());
        String password = credentialResolver.resolve(account);
        MimeMessage mimeMessage = buildMessage(account, password, request, attachments);
        try {
            Transport.send(mimeMessage);
        } catch (MessagingException e) {
            log.warn("邮件发送失败: accountId={}, reason={}", account.getId(), e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.send.failed",
                    "邮件发送失败：" + e.getMessage(), e.getMessage());
        }
        indexSent(account, request, mimeMessage, attachments);
        log.info("邮件已发送: accountId={}, to={}, subject={}, attachments={}",
                account.getId(), MailAddressUtils.join(request.getTo()), request.getSubject(), attachments.size());
    }

    /**
     * 按 fileId 从文件服务取附件字节。
     *
     * <p>发送前一次性取回全部附件：SMTP 事务过程中再发起 HTTP 调用会让连接保持时间
     * 随附件数量线性增长，超时风险更高。</p>
     *
     * @param requests 附件引用
     * @return 附件内容
     */
    private List<SendAttachment> loadAttachments(List<MailAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<SendAttachment> result = new ArrayList<>(requests.size());
        long total = 0L;
        for (MailAttachmentRequest item : requests) {
            byte[] content = fileClient.download(item.getFileId());
            total += content.length;
            if (properties.getMaxSendSizeBytes() > 0 && total > properties.getMaxSendSizeBytes()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "mail.send.attachment-total-exceeded",
                        "附件总大小超出邮件上限");
            }
            result.add(new SendAttachment(item.getFileId(), item.getFileName(),
                    StringUtils.hasText(item.getContentType()) ? item.getContentType() : "application/octet-stream",
                    content));
        }
        return result;
    }

    /**
     * 构建 MIME 消息
     *
     * @param account 发信账号
     * @param password 凭据
     * @param request 发信请求
     * @param attachments 已取回字节的附件
     * @return MIME 消息
     */
    private MimeMessage buildMessage(MailAccount account, String password, MailSendRequest request,
                                     List<SendAttachment> attachments) {
        boolean ssl = account.getSmtpSsl() != null && account.getSmtpSsl() == 1;
        Properties props = new Properties();
        props.put("mail.smtp.host", account.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(account.getSmtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(READ_TIMEOUT_MS));
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account.getEmail(), password);
            }
        });
        try {
            MimeMessage mimeMessage = new MimeMessage(session);
            String displayName = StringUtils.hasText(account.getDisplayName())
                    ? account.getDisplayName() : account.getEmail();
            mimeMessage.setFrom(new InternetAddress(account.getEmail(), displayName,
                    StandardCharsets.UTF_8.name()));
            mimeMessage.setRecipients(Message.RecipientType.TO, MailAddressUtils.join(request.getTo()));
            String cc = MailAddressUtils.join(request.getCc());
            if (cc != null) {
                mimeMessage.setRecipients(Message.RecipientType.CC, cc);
            }
            String bcc = MailAddressUtils.join(request.getBcc());
            if (bcc != null) {
                mimeMessage.setRecipients(Message.RecipientType.BCC, bcc);
            }
            mimeMessage.setSubject(request.getSubject() == null ? "" : request.getSubject(),
                    StandardCharsets.UTF_8.name());
            mimeMessage.setSentDate(new Date());
            applyContent(mimeMessage, request, attachments);
            mimeMessage.saveChanges();
            long estimated = estimateSize(request, attachments);
            if (estimated > properties.getMaxSendSizeBytes()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "mail.send.size-exceeded", "邮件超出大小上限");
            }
            return mimeMessage;
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.send.content-invalid",
                    "邮件内容不合法：" + e.getMessage(), e.getMessage());
        }
    }

    /**
     * 组装邮件内容：无附件时保持简单的 text/alternative 结构，有附件时外层用 mixed。
     *
     * <p>不能一律用 mixed：很多客户端对「mixed 里只有一个 text/plain」的邮件
     * 会显示为带附件的正文，观感像多了一个空附件。</p>
     *
     * @param mimeMessage 消息
     * @param request 发信请求
     * @param attachments 附件
     * @throws MessagingException 协议异常
     * @throws java.io.UnsupportedEncodingException 编码异常
     */
    private void applyContent(MimeMessage mimeMessage, MailSendRequest request, List<SendAttachment> attachments)
            throws MessagingException, java.io.UnsupportedEncodingException {
        if (attachments.isEmpty()) {
            // 无附件：直接把正文设到消息上，保持 text / html / alternative 三种简单结构
            applyBodyDirect(mimeMessage, request);
            return;
        }
        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(buildBodyPart(request));
        for (SendAttachment attachment : attachments) {
            mixed.addBodyPart(buildAttachmentPart(attachment));
        }
        mimeMessage.setContent(mixed);
    }

    /**
     * 无附件时的正文设置：同时有纯文本与 HTML 用 alternative，客户端自行择优展示。
     *
     * @param mimeMessage 消息
     * @param request 发信请求
     * @throws MessagingException 协议异常
     * @throws java.io.UnsupportedEncodingException 编码异常
     */
    private void applyBodyDirect(MimeMessage mimeMessage, MailSendRequest request)
            throws MessagingException, java.io.UnsupportedEncodingException {
        boolean hasHtml = StringUtils.hasText(request.getBodyHtml());
        boolean hasText = StringUtils.hasText(request.getBodyText());
        if (hasHtml && hasText) {
            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(request.getBodyText(), StandardCharsets.UTF_8.name(), "plain");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(request.getBodyHtml(), "text/html; charset=UTF-8");
            alternative.addBodyPart(textPart);
            alternative.addBodyPart(htmlPart);
            mimeMessage.setContent(alternative);
            return;
        }
        if (hasHtml) {
            mimeMessage.setContent(request.getBodyHtml(), "text/html; charset=UTF-8");
            return;
        }
        mimeMessage.setText(hasText ? request.getBodyText() : "", StandardCharsets.UTF_8.name());
    }

    /**
     * 构建正文部件
     *
     * @param request 发信请求
     * @return 正文部件
     * @throws MessagingException 协议异常
     * @throws java.io.UnsupportedEncodingException 编码异常
     */
    private MimeBodyPart buildBodyPart(MailSendRequest request)
            throws MessagingException, java.io.UnsupportedEncodingException {
        boolean hasHtml = StringUtils.hasText(request.getBodyHtml());
        boolean hasText = StringUtils.hasText(request.getBodyText());
        MimeBodyPart bodyPart = new MimeBodyPart();
        if (hasHtml && hasText) {
            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(request.getBodyText(), StandardCharsets.UTF_8.name(), "plain");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(request.getBodyHtml(), "text/html; charset=UTF-8");
            alternative.addBodyPart(textPart);
            alternative.addBodyPart(htmlPart);
            bodyPart.setContent(alternative);
            return bodyPart;
        }
        if (hasHtml) {
            bodyPart.setContent(request.getBodyHtml(), "text/html; charset=UTF-8");
            return bodyPart;
        }
        bodyPart.setText(hasText ? request.getBodyText() : "", StandardCharsets.UTF_8.name());
        return bodyPart;
    }

    /**
     * 构建附件部件（非内联，客户端显示为附件列表项）。
     *
     * @param attachment 附件
     * @return 附件部件
     * @throws MessagingException 协议异常
     */
    private MimeBodyPart buildAttachmentPart(SendAttachment attachment) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(
                new ByteArrayDataSource(attachment.content(), attachment.contentType())));
        part.setFileName(StringUtils.hasText(attachment.fileName()) ? attachment.fileName() : "attachment");
        part.setDisposition(jakarta.mail.Part.ATTACHMENT);
        return part;
    }

    /**
     * 估算邮件大小（正文按字符数近似，附件按实际字节）
     *
     * @param request 发信请求
     * @param attachments 附件
     * @return 估算字节数
     */
    private long estimateSize(MailSendRequest request, List<SendAttachment> attachments) {
        long size = 0L;
        if (request.getBodyHtml() != null) {
            size += request.getBodyHtml().getBytes(StandardCharsets.UTF_8).length;
        }
        if (request.getBodyText() != null) {
            size += request.getBodyText().getBytes(StandardCharsets.UTF_8).length;
        }
        for (SendAttachment attachment : attachments) {
            size += attachment.content().length;
        }
        return size;
    }

    /**
     * 写入已发送索引
     *
     * <p>UID 取负值：IMAP 服务器上的 UID 恒为正，负数标记「本地副本」，
     * 后续同步时按 Message-ID 去重，不会与服务器上的同一封邮件重复入库。</p>
     *
     * @param account 账号
     * @param request 发信请求
     * @param mimeMessage 已发送的消息
     * @param attachments 附件（已取回字节）
     */
    private void indexSent(MailAccount account, MailSendRequest request, MimeMessage mimeMessage,
                           List<SendAttachment> attachments) {
        MailFolder sent = folderService.findByCode(account.getId(), FOLDER_SENT);
        if (sent == null) {
            log.warn("已发送文件夹缺失，跳过本地副本索引: accountId={}", account.getId());
            return;
        }
        MailMessage message = new MailMessage();
        message.setAccountId(account.getId());
        message.setFolderId(sent.getId());
        // 本地副本没有 IMAP 真实位置，锚点同样指向「已发送」：
        // 这个字段非空是硬约束，而负数 UID 已经保证回源逻辑不会真的去服务器找它
        message.setImapFolderId(sent.getId());
        message.setUid(-IdWorker.getId());
        message.setMessageId(safeMessageId(mimeMessage));
        message.setSubject(request.getSubject() == null ? "（无主题）" : request.getSubject());
        message.setFromAddr(account.getEmail());
        message.setFromName(account.getDisplayName());
        message.setToAddrs(MailAddressUtils.join(request.getTo()));
        message.setCcAddrs(MailAddressUtils.join(request.getCc()));
        message.setBccAddrs(MailAddressUtils.join(request.getBcc()));
        message.setSentTime(LocalDateTime.now());
        message.setSizeBytes(estimateSize(request, attachments));
        message.setHasAttachment(attachments.isEmpty() ? 0 : 1);
        message.setSeen(1);
        message.setFlagged(0);
        message.setAnswered(0);
        message.setForwarded(0);
        message.setPriority(3);
        message.setPreview(preview(request));
        message.setBodyText(request.getBodyText());
        message.setBodyHtml(request.getBodyHtml());
        messageMapper.insert(message);
        indexAttachments(message.getId(), attachments);
        folderService.refreshCounters(sent.getId());
    }

    /**
     * 登记已发送邮件的附件元信息。
     *
     * <p>只写引用不复制字节：附件已在 {@code whale-file}，再存一份既浪费空间，
     * 也会让「原件被清理」与「副本仍可下载」两个事实互相矛盾。</p>
     *
     * @param messageId 邮件ID
     * @param attachments 附件
     */
    private void indexAttachments(Long messageId, List<SendAttachment> attachments) {
        for (SendAttachment item : attachments) {
            MailAttachment attachment = new MailAttachment();
            attachment.setMessageId(messageId);
            attachment.setFileId(item.fileId());
            attachment.setFileName(item.fileName());
            attachment.setContentType(item.contentType());
            attachment.setSizeBytes((long) item.content().length);
            attachment.setInlineFlag(0);
            attachmentMapper.insert(attachment);
        }
    }

    /**
     * 发送用附件快照：fileId 与已取回的字节。
     *
     * @param fileId whale-file 文件ID
     * @param fileName 文件名
     * @param contentType MIME 类型
     * @param content 字节内容
     */
    private record SendAttachment(Long fileId, String fileName, String contentType, byte[] content) {
    }

    /**
     * 读取 Message-ID（失败不阻断）
     *
     * @param mimeMessage 消息
     * @return Message-ID
     */
    private String safeMessageId(MimeMessage mimeMessage) {
        try {
            return mimeMessage.getMessageID();
        } catch (MessagingException e) {
            return null;
        }
    }

    /**
     * 生成摘要
     *
     * @param request 发信请求
     * @return 摘要
     */
    private String preview(MailSendRequest request) {
        String source = StringUtils.hasText(request.getBodyText())
                ? request.getBodyText()
                : String.valueOf(request.getBodyHtml()).replaceAll("<[^>]+>", " ");
        String normalized = source.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }
}
