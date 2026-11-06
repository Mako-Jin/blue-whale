package com.bluewhale.mail.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailFolder;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * IMAP 协议适配：只负责连接、增量拉取头部、按 UID 回源正文，不做入库。
 *
 * <p>三条设计约束：</p>
 * <ol>
 *   <li><b>同步只拉 header</b>：正文按「打开详情时回源」获取，避免首同步把整箱邮件
 *       的二进制拉进 MySQL；大附件永远不下载，只在正文解析阶段打标记。</li>
 *   <li><b>每次操作独立短连接</b>：IMAP 长连接在容器重启、NAT 超时后状态不可信，
 *       短连接 + 超时上限比连接池更稳。</li>
 *   <li><b>UIDVALIDITY 变化即重建</b>：由调用方比对后清空该文件夹索引，本类只上报数值。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailImapSupport {

    /** 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** 读超时（毫秒） */
    private static final int READ_TIMEOUT_MS = 30_000;

    /** MIME 递归深度上限：防御畸形邮件的无限嵌套 */
    private static final int MAX_MIME_DEPTH = 6;

    private final MailCredentialResolver credentialResolver;

    /**
     * 列出服务器上的全部可选文件夹。
     *
     * <p>用途是让本地索引跟上服务器变化：用户在 Webmail 或客户端新建的文件夹，
     * 平台侧必须能发现，否则界面上永远只有系统文件夹。只返回可存邮件的节点，
     * {@code \Noselect} 的纯容器（如只有子文件夹的父级）会被丢弃。</p>
     *
     * <p>失败直接抛异常：文件夹探测是同步的前置步骤，静默返回空列表会让调用方
     * 误判「服务器上什么都没有」，进而把本地文件夹当成已删除。</p>
     *
     * @param account 邮箱账号
     * @return 文件夹快照列表
     */
    public List<RemoteFolder> listFolders(MailAccount account) {
        Store store = null;
        try {
            store = connect(account);
            jakarta.mail.Folder[] found = store.getDefaultFolder().list("*");
            List<RemoteFolder> result = new ArrayList<>();
            for (jakarta.mail.Folder folder : found) {
                if (folder == null) {
                    continue;
                }
                String name = folder.getFullName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                boolean selectable = (folder.getType() & jakarta.mail.Folder.HOLDS_MESSAGES) != 0;
                char separator = folder.getSeparator();
                // JavaMail 用 0 表示「该命名空间没有层级分隔符」，转成 null 让上层走默认口径
                result.add(new RemoteFolder(name, separator == 0 ? null : String.valueOf(separator), selectable));
            }
            log.debug("IMAP 文件夹探测完成: accountId={}, count={}", account.getId(), result.size());
            return result;
        } catch (MessagingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "IMAP 文件夹探测失败：" + e.getMessage());
        } finally {
            closeStore(store);
        }
    }

    /**
     * 增量拉取指定文件夹的邮件头部
     *
     * @param account 邮箱账号
     * @param folder 本地文件夹（提供 imapName 与 lastUid 位点）
     * @param limit 单次最多返回条数
     * @return 拉取结果
     */
    public FetchResult fetchSince(MailAccount account, MailFolder folder, int limit) {
        Store store = null;
        try {
            store = connect(account);
            jakarta.mail.Folder imapFolder = store.getFolder(folder.getImapName());
            if (imapFolder == null || !imapFolder.exists()) {
                log.debug("IMAP 文件夹不存在，跳过: accountId={}, imapName={}", account.getId(), folder.getImapName());
                return FetchResult.empty();
            }
            imapFolder.open(jakarta.mail.Folder.READ_ONLY);
            try {
                if (!(imapFolder instanceof UIDFolder uidFolder)) {
                    log.warn("IMAP 服务器不支持 UID，跳过增量同步: accountId={}", account.getId());
                    return FetchResult.empty();
                }
                long uidValidity = uidFolder.getUIDValidity();
                long lastUid = folder.getLastUid() == null ? 0L : folder.getLastUid();
                Message[] messages = selectMessages(uidFolder, imapFolder, lastUid);
                int start = Math.max(0, messages.length - Math.max(limit, 1));
                List<FetchedMail> result = new ArrayList<>();
                for (int i = start; i < messages.length; i++) {
                    long uid = uidFolder.getUID(messages[i]);
                    if (uid <= 0 || (lastUid > 0 && uid <= lastUid)) {
                        continue;
                    }
                    result.add(toFetched(messages[i], uid));
                }
                log.debug("IMAP 拉取完成: accountId={}, folder={}, uidValidity={}, count={}",
                        account.getId(), folder.getImapName(), uidValidity, result.size());
                return new FetchResult(result, uidValidity);
            } finally {
                closeFolder(imapFolder);
            }
        } catch (MessagingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "IMAP 同步失败：" + e.getMessage());
        } finally {
            closeStore(store);
        }
    }

    /**
     * 按 UID 回源正文
     *
     * <p>失败不抛异常：详情页宁可展示空正文并提示，也不该因一封畸形邮件整页 500。</p>
     *
     * @param account 邮箱账号
     * @param folder 本地文件夹
     * @param uid IMAP UID
     * @return 正文快照；失败返回 {@link MailBody#EMPTY}
     */
    public MailBody fetchBody(MailAccount account, MailFolder folder, long uid) {
        Store store = null;
        try {
            store = connect(account);
            jakarta.mail.Folder imapFolder = store.getFolder(folder.getImapName());
            if (imapFolder == null || !imapFolder.exists()) {
                return MailBody.EMPTY;
            }
            imapFolder.open(jakarta.mail.Folder.READ_ONLY);
            try {
                if (!(imapFolder instanceof UIDFolder uidFolder)) {
                    return MailBody.EMPTY;
                }
                Message message = uidFolder.getMessageByUID(uid);
                return message == null ? MailBody.EMPTY : extractBody(message, 0);
            } finally {
                closeFolder(imapFolder);
            }
        } catch (Exception e) {
            log.warn("邮件正文回源失败: accountId={}, uid={}, reason={}", account.getId(), uid, e.getMessage());
            return MailBody.EMPTY;
        } finally {
            closeStore(store);
        }
    }

    /**
     * 按 UID 回源附件字节。
     *
     * <p>与正文回源一致，只在用户真正需要时（打开详情首次登记、或下载某个附件）调用，
     * 同步阶段不下载任何附件——否则首同步就会把整箱带附件邮件拉下来。</p>
     *
     * @param account 邮箱账号
     * @param folder 本地文件夹
     * @param uid IMAP UID
     * @param maxBytes 单个附件字节上限；超过上限的部件只保留元信息，内容为 {@code null}
     * @return 附件快照列表；拉取失败抛业务异常（下载场景需要明确失败，不能静默空列表）
     */
    public List<FetchedAttachment> fetchAttachments(MailAccount account, MailFolder folder, long uid,
                                                    long maxBytes) {
        Store store = null;
        try {
            store = connect(account);
            jakarta.mail.Folder imapFolder = store.getFolder(folder.getImapName());
            if (imapFolder == null || !imapFolder.exists()) {
                return List.of();
            }
            imapFolder.open(jakarta.mail.Folder.READ_ONLY);
            try {
                if (!(imapFolder instanceof UIDFolder uidFolder)) {
                    return List.of();
                }
                Message message = uidFolder.getMessageByUID(uid);
                if (message == null) {
                    return List.of();
                }
                List<FetchedAttachment> collected = new ArrayList<>();
                collectAttachments(message, 0, maxBytes, collected);
                return collected;
            } finally {
                closeFolder(imapFolder);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("邮件附件回源失败: accountId={}, uid={}, reason={}", account.getId(), uid, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件拉取失败：" + e.getMessage());
        } finally {
            closeStore(store);
        }
    }

    /**
     * 递归收集附件部件。
     *
     * @param part 当前部件
     * @param depth 当前 MIME 深度
     * @param maxBytes 单附件字节上限
     * @param out 结果集
     * @throws MessagingException 协议异常
     * @throws IOException 读取内容异常
     */
    private void collectAttachments(Part part, int depth, long maxBytes, List<FetchedAttachment> out)
            throws MessagingException, IOException {
        if (depth > MAX_MIME_DEPTH) {
            log.warn("MIME 嵌套超过 {} 层，跳过剩余部件", MAX_MIME_DEPTH);
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    collectAttachments(multipart.getBodyPart(i), depth + 1, maxBytes, out);
                }
            }
            return;
        }
        String fileName = part.getFileName();
        boolean inline = Part.INLINE.equalsIgnoreCase(part.getDisposition());
        // 内联 cid 图片同样要收：正文里的图片没有它就显示不出来
        boolean candidate = isAttachment(part) || (inline && fileName != null && !fileName.isBlank());
        if (!candidate) {
            return;
        }
        long declared = part.getSize();
        if (maxBytes > 0 && declared > maxBytes) {
            out.add(new FetchedAttachment(fileName, part.getContentType(), contentIdOf(part), inline,
                    declared, null));
            return;
        }
        byte[] bytes = part.getInputStream().readAllBytes();
        if (maxBytes > 0 && bytes.length > maxBytes) {
            out.add(new FetchedAttachment(fileName, part.getContentType(), contentIdOf(part), inline,
                    bytes.length, null));
            return;
        }
        out.add(new FetchedAttachment(fileName, part.getContentType(), contentIdOf(part), inline,
                bytes.length, bytes));
    }

    /**
     * 读取 Content-ID 并去掉尖括号（前端按 cid 匹配正文占位）。
     *
     * @param part 部件
     * @return 归一后的 Content-ID；无则返回 {@code null}
     * @throws MessagingException 协议异常
     */
    private static String contentIdOf(Part part) throws MessagingException {
        String[] ids = part.getHeader("Content-ID");
        if (ids == null || ids.length == 0 || ids[0] == null) {
            return null;
        }
        String value = ids[0].trim();
        return value.startsWith("<") && value.endsWith(">") ? value.substring(1, value.length() - 1) : value;
    }

    /**
     * 选择待拉取的消息区间
     *
     * @param uidFolder UID 能力
     * @param imapFolder 文件夹
     * @param lastUid 已同步位点
     * @return 消息数组
     * @throws MessagingException 协议异常
     */
    private Message[] selectMessages(UIDFolder uidFolder, jakarta.mail.Folder imapFolder, long lastUid)
            throws MessagingException {
        if (lastUid > 0) {
            try {
                return uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
            } catch (MessagingException e) {
                // 部分服务器不支持 UID 区间查询，回落全量后由调用方截尾
                log.debug("UID 区间查询不受支持，回落全量列表: {}", e.getMessage());
            }
        }
        return imapFolder.getMessages();
    }

    /**
     * 头部快照转换
     *
     * @param message 消息
     * @param uid UID
     * @return 快照
     * @throws MessagingException 协议异常
     */
    private FetchedMail toFetched(Message message, long uid) throws MessagingException {
        String fromAddr = null;
        String fromName = null;
        Address[] from = message.getFrom();
        if (from != null && from.length > 0 && from[0] instanceof InternetAddress address) {
            fromAddr = address.getAddress();
            fromName = address.getPersonal();
        }
        String subject = message.getSubject();
        LocalDateTime sentTime = null;
        Date sentDate = message.getSentDate();
        if (sentDate != null) {
            sentTime = LocalDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault());
        }
        int rawSize = message.getSize();
        Flags flags = message.getFlags();
        return new FetchedMail(
                uid,
                headerOf(message, "Message-ID"),
                subject == null || subject.isBlank() ? "（无主题）" : subject,
                fromAddr,
                fromName,
                addressesOf(message.getRecipients(Message.RecipientType.TO)),
                addressesOf(message.getRecipients(Message.RecipientType.CC)),
                sentTime,
                rawSize > 0 ? rawSize : 0L,
                flags != null && flags.contains(Flags.Flag.SEEN),
                flags != null && flags.contains(Flags.Flag.FLAGGED));
    }

    /**
     * 读取指定头部字段
     *
     * @param part 邮件部件
     * @param name 头名
     * @return 头值
     * @throws MessagingException 协议异常
     */
    private String headerOf(Part part, String name) throws MessagingException {
        String[] values = part.getHeader(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    /**
     * 地址数组转逗号分隔字符串
     *
     * @param addresses 地址数组
     * @return 逗号分隔字符串；无地址返回 {@code null}
     */
    private String addressesOf(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        List<String> values = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address instanceof InternetAddress internetAddress && internetAddress.getAddress() != null) {
                values.add(internetAddress.getAddress());
            } else if (address != null) {
                values.add(address.toString());
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    /**
     * 递归解析 MIME 正文
     *
     * @param part 部件
     * @param depth 递归深度
     * @return 正文快照
     * @throws MessagingException 协议异常
     * @throws IOException 读流异常
     */
    private MailBody extractBody(Part part, int depth) throws MessagingException, IOException {
        if (depth > MAX_MIME_DEPTH) {
            return MailBody.EMPTY;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart multipart)) {
                return MailBody.EMPTY;
            }
            String text = null;
            String html = null;
            boolean attachment = false;
            for (int i = 0; i < multipart.getCount(); i++) {
                MailBody child = extractBody(multipart.getBodyPart(i), depth + 1);
                if (text == null && child.text() != null) {
                    text = child.text();
                }
                if (html == null && child.html() != null) {
                    html = child.html();
                }
                attachment = attachment || child.hasAttachment();
            }
            return new MailBody(text, html, attachment);
        }
        boolean attachment = isAttachment(part);
        if (attachment) {
            // 附件二进制不下载：只标记存在，正文由前端经 whale-file 获取
            return new MailBody(null, null, true);
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String value ? new MailBody(value, null, false) : MailBody.EMPTY;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content instanceof String value ? new MailBody(null, value, false) : MailBody.EMPTY;
        }
        return MailBody.EMPTY;
    }

    /**
     * 判断是否为独立附件（内联 cid 资源不计入「有附件」）
     *
     * @param part 部件
     * @return 独立附件返回 {@code true}
     * @throws MessagingException 协议异常
     */
    private boolean isAttachment(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        String fileName = part.getFileName();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return true;
        }
        return fileName != null && !fileName.isBlank() && !Part.INLINE.equalsIgnoreCase(disposition);
    }

    /**
     * 建立 IMAP 连接
     *
     * @param account 邮箱账号
     * @return 已连接的 Store
     * @throws MessagingException 协议异常
     */
    private Store connect(MailAccount account) throws MessagingException {
        String password = credentialResolver.resolve(account);
        boolean ssl = account.getImapSsl() != null && account.getImapSsl() == 1;
        String protocol = ssl ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail." + protocol + ".timeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail." + protocol + ".writetimeout", String.valueOf(READ_TIMEOUT_MS));
        if (ssl) {
            props.put("mail.imaps.ssl.enable", "true");
        }
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(account.getImapHost(), account.getImapPort(), account.getEmail(), password);
        return store;
    }

    /**
     * 安全关闭文件夹
     *
     * @param folder 文件夹
     */
    private void closeFolder(jakarta.mail.Folder folder) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (MessagingException e) {
            log.debug("关闭 IMAP 文件夹失败: {}", e.getMessage());
        }
    }

    /**
     * 安全关闭连接
     *
     * @param store 连接
     */
    private void closeStore(Store store) {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (MessagingException e) {
            log.debug("关闭 IMAP 连接失败: {}", e.getMessage());
        }
    }
}
