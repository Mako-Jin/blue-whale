package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.dto.MailMessageQueryRequest;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailAttachment;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.mapper.MailAttachmentMapper;
import com.bluewhale.mail.mapper.MailFolderMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailAttachmentService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.service.MailMessageService;
import com.bluewhale.mail.support.MailBody;
import com.bluewhale.mail.support.MailImapSupport;
import com.bluewhale.mail.vo.MailAttachmentVO;
import com.bluewhale.mail.vo.MailMessageVO;
import com.bluewhale.mail.vo.MailUnreadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 邮件查询与状态服务实现。
 *
 * <p>正文采用「详情页惰性回源」：列表只读索引，打开详情时若本地无正文才连 IMAP 拉一次并回填。
 * 这样首同步只产生 header 流量，日常翻页也不会触碰正文列。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailMessageServiceImpl implements MailMessageService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 摘要最大长度 */
    private static final int PREVIEW_LENGTH = 200;

    /** 已读 */
    private static final int FLAG_ON = 1;

    /** 未读 */
    private static final int FLAG_OFF = 0;

    private final MailMessageMapper messageMapper;

    private final MailAttachmentMapper attachmentMapper;

    private final MailFolderMapper folderMapper;

    private final MailAccountMapper accountMapper;

    private final MailAccountService accountService;

    private final MailFolderService folderService;

    private final MailImapSupport imapSupport;

    private final MailAttachmentService attachmentService;

    @Override
    public PageResult<MailMessageVO> page(MailMessageQueryRequest request, Long userId) {
        Long accountId = request.getAccountId() == null
                ? accountService.requireDefaultAccountId(userId) : request.getAccountId();
        accountService.requireOwned(accountId, userId);
        MailFolder folder = resolveFolder(accountId, request);

        LambdaQueryWrapper<MailMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MailMessage::getAccountId, accountId);
        wrapper.eq(folder != null, MailMessage::getFolderId, folder == null ? null : folder.getId());
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(w -> w.like(MailMessage::getSubject, keyword)
                    .or().like(MailMessage::getFromAddr, keyword)
                    .or().like(MailMessage::getPreview, keyword));
        }
        wrapper.eq(request.getSeen() != null, MailMessage::getSeen, request.getSeen());
        wrapper.eq(request.getHasAttachment() != null, MailMessage::getHasAttachment, request.getHasAttachment());
        wrapper.eq(StringUtils.hasText(request.getFromAddr()), MailMessage::getFromAddr, request.getFromAddr());
        wrapper.ge(request.getStartTime() != null, MailMessage::getSentTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, MailMessage::getSentTime, request.getEndTime());
        wrapper.orderByDesc(MailMessage::getSentTime).orderByDesc(MailMessage::getId);

        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MailMessage> page = messageMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);

        Map<Long, String> folderCodes = resolveFolderCodes(accountId, folder);
        List<MailMessageVO> records = page.getRecords().stream()
                .map(message -> toVo(message, folderCodes.get(message.getFolderId()), List.of()))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MailMessageVO detail(Long id, Long userId) {
        MailMessage message = requireMessage(id, userId);
        MailAccount account = accountService.requireOwned(message.getAccountId(), userId);
        MailFolder folder = message.getFolderId() == null ? null : folderMapper.selectById(message.getFolderId());
        if (needsBody(message) && folder != null) {
            message = hydrateBody(message, account, folder);
        }
        // 附件与正文同样按需登记：首次打开详情才连 IMAP 取附件并写入文件服务，
        // 已有登记直接复用，重复打开不会产生额外下载与存储
        List<MailAttachment> attachmentEntities = folder == null
                ? attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                        .eq(MailAttachment::getMessageId, message.getId()))
                : attachmentService.ensureIngested(message, account, folder);
        List<MailAttachmentVO> attachments = attachmentEntities.stream()
                .map(MailMessageServiceImpl::toAttachmentVo)
                .toList();
        return toVo(message, folder == null ? null : folder.getFolderCode(), attachments);
    }

    @Override
    public void markSeen(Long id, Integer seen, Long userId) {
        MailMessage message = requireMessage(id, userId);
        int value = seen != null && seen == FLAG_ON ? FLAG_ON : FLAG_OFF;
        if (Objects.equals(message.getSeen(), value)) {
            return;
        }
        message.setSeen(value);
        messageMapper.updateById(message);
        folderService.refreshCounters(message.getFolderId());
    }

    @Override
    public void markFlagged(Long id, Integer flagged, Long userId) {
        MailMessage message = requireMessage(id, userId);
        message.setFlagged(flagged != null && flagged == FLAG_ON ? FLAG_ON : FLAG_OFF);
        messageMapper.updateById(message);
    }

    @Override
    public void moveToTrash(Long id, Long userId) {
        MailMessage message = requireMessage(id, userId);
        MailFolder trash = folderService.findByCode(message.getAccountId(), "TRASH");
        if (trash == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "回收站文件夹缺失，请重新初始化邮箱账号");
        }
        if (Objects.equals(message.getFolderId(), trash.getId())) {
            return;
        }
        Long originFolderId = message.getFolderId();
        message.setFolderId(trash.getId());
        try {
            messageMapper.updateById(message);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回收站中已存在同一封邮件");
        }
        folderService.refreshCounters(originFolderId);
        folderService.refreshCounters(trash.getId());
    }

    @Override
    public MailUnreadVO unread(Long userId) {
        List<MailAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getUserId, userId)
                .eq(MailAccount::getStatus, 1));
        MailUnreadVO vo = new MailUnreadVO();
        if (accounts.isEmpty()) {
            vo.setTotal(0L);
            vo.setByFolder(Map.of());
            return vo;
        }
        List<Long> accountIds = accounts.stream().map(MailAccount::getId).toList();
        List<MailFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<MailFolder>()
                .in(MailFolder::getAccountId, accountIds));
        Map<String, Long> byFolder = new HashMap<>();
        long total = 0L;
        for (MailFolder folder : folders) {
            long unread = folder.getUnreadCount() == null ? 0L : folder.getUnreadCount();
            if (unread <= 0) {
                continue;
            }
            total += unread;
            byFolder.merge(folderKey(folder), unread, Long::sum);
        }
        vo.setTotal(total);
        vo.setByFolder(byFolder);
        return vo;
    }

    /**
     * 未读统计的文件夹键
     *
     * <p>系统文件夹用稳定编码（{@code INBOX} 等），自定义文件夹用 {@code CUSTOM:<id>}：
     * 自定义文件夹的编码都是 {@code CUSTOM}，只按编码聚合会让侧边栏每个自定义文件夹
     * 显示同一个角标，未读总数也会被重复计入。</p>
     *
     * @param folder 文件夹
     * @return 统计键
     */
    private static String folderKey(MailFolder folder) {
        return "CUSTOM".equals(folder.getFolderType())
                ? folder.getFolderCode() + ":" + folder.getId()
                : folder.getFolderCode();
    }

    /**
     * 解析目标文件夹
     *
     * @param accountId 账号ID
     * @param request 查询条件
     * @return 文件夹；未指定时取收件箱
     */
    private MailFolder resolveFolder(Long accountId, MailMessageQueryRequest request) {
        if (request.getFolderId() != null) {
            MailFolder folder = folderMapper.selectById(request.getFolderId());
            if (folder == null || !Objects.equals(folder.getAccountId(), accountId)) {
                throw new BizException(ErrorCode.NOT_FOUND, "文件夹不存在");
            }
            return folder;
        }
        return folderService.findByCode(accountId,
                StringUtils.hasText(request.getFolderCode()) ? request.getFolderCode() : "INBOX");
    }

    /**
     * 构建文件夹ID → 编码映射
     *
     * @param accountId 账号ID
     * @param folder 已确定的文件夹，可为 {@code null}
     * @return 映射
     */
    private Map<Long, String> resolveFolderCodes(Long accountId, MailFolder folder) {
        if (folder != null) {
            return Map.of(folder.getId(), folder.getFolderCode());
        }
        return folderMapper.selectList(new LambdaQueryWrapper<MailFolder>()
                        .eq(MailFolder::getAccountId, accountId))
                .stream()
                .collect(Collectors.toMap(MailFolder::getId, MailFolder::getFolderCode, (a, b) -> a));
    }

    /**
     * 校验邮件归属
     *
     * @param id 邮件ID
     * @param userId 当前用户ID
     * @return 邮件实体
     */
    private MailMessage requireMessage(Long id, Long userId) {
        MailMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "邮件不存在");
        }
        accountService.requireOwned(message.getAccountId(), userId);
        return message;
    }

    /**
     * 是否需要回源正文
     *
     * @param message 邮件
     * @return 需要回源返回 {@code true}
     */
    private boolean needsBody(MailMessage message) {
        boolean empty = !StringUtils.hasText(message.getBodyText()) && !StringUtils.hasText(message.getBodyHtml());
        // uid 为负数是本地写入的已发送副本，无 IMAP 回源目标
        return empty && message.getUid() != null && message.getUid() > 0;
    }

    /**
     * 回源正文并回填索引
     *
     * @param message 邮件
     * @param account 账号
     * @param folder 文件夹
     * @return 回填后的邮件
     */
    private MailMessage hydrateBody(MailMessage message, MailAccount account, MailFolder folder) {
        MailBody body = imapSupport.fetchBody(account, folder, message.getUid());
        if (body.isEmpty()) {
            return message;
        }
        message.setBodyText(body.text());
        message.setBodyHtml(body.html());
        if (!StringUtils.hasText(message.getPreview())) {
            message.setPreview(body.preview(PREVIEW_LENGTH));
        }
        if (body.hasAttachment()) {
            message.setHasAttachment(1);
        }
        messageMapper.updateById(message);
        return message;
    }

    /**
     * 实体转视图
     *
     * @param message 邮件
     * @param folderCode 文件夹编码
     * @param attachments 附件
     * @return 视图
     */
    private static MailMessageVO toVo(MailMessage message, String folderCode, List<MailAttachmentVO> attachments) {
        MailMessageVO vo = new MailMessageVO();
        vo.setId(message.getId());
        vo.setFolderId(message.getFolderId());
        vo.setFolderCode(folderCode);
        vo.setSubject(message.getSubject());
        vo.setFromAddr(message.getFromAddr());
        vo.setFromName(message.getFromName());
        vo.setToAddrs(message.getToAddrs());
        vo.setCcAddrs(message.getCcAddrs());
        vo.setBccAddrs(message.getBccAddrs());
        vo.setSentTime(message.getSentTime());
        vo.setSizeBytes(message.getSizeBytes());
        vo.setHasAttachment(message.getHasAttachment());
        vo.setSeen(message.getSeen());
        vo.setFlagged(message.getFlagged());
        vo.setPreview(message.getPreview());
        vo.setBodyText(message.getBodyText());
        vo.setBodyHtml(message.getBodyHtml());
        vo.setAttachments(attachments);
        return vo;
    }

    /**
     * 附件实体转视图
     *
     * @param attachment 附件实体
     * @return 附件视图
     */
    private static MailAttachmentVO toAttachmentVo(MailAttachment attachment) {
        MailAttachmentVO vo = new MailAttachmentVO();
        vo.setId(attachment.getId());
        vo.setFileId(attachment.getFileId());
        vo.setFileName(attachment.getFileName());
        vo.setContentType(attachment.getContentType());
        vo.setSizeBytes(attachment.getSizeBytes());
        vo.setInlineFlag(attachment.getInlineFlag());
        vo.setContentId(attachment.getContentId());
        return vo;
    }
}
