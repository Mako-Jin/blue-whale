package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailMessageQueryRequest;
import com.bluewhale.mail.dto.MailSearchQuery;
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
import com.bluewhale.mail.service.MailDeliveryReportService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.service.MailMessageService;
import com.bluewhale.mail.service.MailSearchService;
import com.bluewhale.mail.support.MailBody;
import com.bluewhale.mail.support.MailImapSupport;
import com.bluewhale.mail.support.MailSearchText;
import com.bluewhale.mail.vo.MailAttachmentVO;
import com.bluewhale.mail.vo.MailMessageVO;
import com.bluewhale.mail.vo.MailUnreadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /** 检索范围：全部文件夹（缺省为当前文件夹） */
    private static final String SCOPE_ALL = "ALL";

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

    private final MailSearchService searchService;

    private final MailDeliveryReportService deliveryReportService;

    private final MailProperties properties;

    @Override
    public PageResult<MailMessageVO> page(MailMessageQueryRequest request, Long userId) {
        Long accountId = request.getAccountId() == null
                ? accountService.requireDefaultAccountId(userId) : request.getAccountId();
        accountService.requireOwned(accountId, userId);
        MailFolder folder = resolveFolder(accountId, request);

        String keyword = MailSearchText.normalize(request.getKeyword());
        if (!keyword.isEmpty()) {
            PageResult<MailMessageVO> searched = searchPage(request, accountId, folder, keyword);
            if (searched != null) {
                return searched;
            }
        }

        LambdaQueryWrapper<MailMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MailMessage::getAccountId, accountId);
        wrapper.eq(folder != null, MailMessage::getFolderId, folder == null ? null : folder.getId());
        if (!keyword.isEmpty()) {
            // 走到这里说明全文检索无命中：索引没随迁移建好、或查询是单个字（ngram 切不出词项）。
            // LIKE 用不上索引，但此时「搜得到」比「搜得快」重要，且只会在无命中时触发一次
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
        // 展示位置与真实位置要分开取：规则归类或移入回收站之后，folderId 已经不是
        // 邮件在服务器上的所在文件夹了；拿它去回源，会按「目标文件夹里同一 UID」取到另一封邮件。
        MailFolder displayFolder = message.getFolderId() == null ? null : folderMapper.selectById(message.getFolderId());
        MailFolder imapFolder = imapFolder(message);
        if (needsBody(message) && imapFolder != null) {
            message = hydrateBody(message, account, imapFolder);
        }
        // 附件与正文同样按需登记：首次打开详情才连 IMAP 取附件并写入文件服务，
        // 已有登记直接复用，重复打开不会产生额外下载与存储
        List<MailAttachment> attachmentEntities = imapFolder == null
                ? attachmentMapper.selectList(new LambdaQueryWrapper<MailAttachment>()
                        .eq(MailAttachment::getMessageId, message.getId()))
                : attachmentService.ensureIngested(message, account, imapFolder);
        List<MailAttachmentVO> attachments = attachmentEntities.stream()
                .map(MailMessageServiceImpl::toAttachmentVo)
                .toList();
        MailMessageVO vo = toVo(message, displayFolder == null ? null : displayFolder.getFolderCode(), attachments);
        // 退信通知在阅读区单独提示：DSN 正文是一段机器可读的 multipart/report，
        // 对用户几乎没有可读性，把关键信息（收件人/状态码/远端应答）结构化展示才有用
        vo.setBounces(deliveryReportService.listByNotify(message.getId()));
        return vo;
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
            throw new BizException(ErrorCode.SYSTEM_ERROR, "mail.folder.trash-missing", "回收站文件夹缺失，请重新初始化邮箱账号");
        }
        if (Objects.equals(message.getFolderId(), trash.getId())) {
            return;
        }
        Long originFolderId = message.getFolderId();
        message.setFolderId(trash.getId());
        // 只改归类位置，不动 imapFolderId：邮件在服务器上仍在原文件夹，
        // 正文与附件还得按原位置回源。唯一键跟着 imap_folder_id 走，因此这里不会撞键。
        messageMapper.updateById(message);
        folderService.refreshCounters(originFolderId);
        folderService.refreshCounters(trash.getId());
        // 文件夹变了要重写索引，否则「在当前文件夹里搜」会把已经移走的邮件漏掉
        searchService.index(message);
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
                throw new BizException(ErrorCode.NOT_FOUND, "mail.folder.not-found", "文件夹不存在");
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
     * 全文检索路径的分页。
     *
     * <p>返回 {@code null} 表示「检索无命中」，由调用方退回 LIKE。之所以不用空结果直接返回，
     * 是因为「索引里没有」和「库里没有」必须区分：索引没建好时直接返回空会让用户以为
     * 邮件丢了，而实际上它就在库里。</p>
     *
     * <p>排序按相关度，不按时间：用户搜关键词时想找的是「最匹配的那封」，
     * 而不是「最近的那封」。筛选条件（已读、附件、时间范围）在检索之后用一次轻量
     * ID 查询收敛，保证总数与列表一致。</p>
     *
     * @param request 查询条件
     * @param accountId 账号ID
     * @param folder 已解析的文件夹
     * @param keyword 归一化后的检索词
     * @return 分页结果；无命中返回 {@code null}
     */
    private PageResult<MailMessageVO> searchPage(MailMessageQueryRequest request, Long accountId,
                                                 MailFolder folder, String keyword) {
        if (MailSearchText.isTooShort(keyword)) {
            return null;
        }
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent(), 1L);
        Long folderFilter = SCOPE_ALL.equalsIgnoreCase(request.getScope()) || folder == null
                ? null : folder.getId();
        List<Long> ids = searchService.searchIds(new MailSearchQuery(accountId, folderFilter, keyword,
                properties.getSearch().getMaxHits()));
        if (ids.isEmpty()) {
            // 关闭兜底时不再回退 LIKE：这条配置是给「大表绝对不能出现 LIKE」的场景用的
            return properties.getSearch().isLikeFallback()
                    ? null : PageResult.of(List.of(), 0L, current, size);
        }
        List<Long> ordered = applySearchFilters(request, ids);
        long total = ordered.size();
        int from = (int) Math.min((current - 1) * size, total);
        int to = (int) Math.min(from + size, total);
        if (from >= to) {
            return PageResult.of(List.of(), total, current, size);
        }
        List<Long> pageIds = ordered.subList(from, to);
        Map<Long, MailMessage> byId = messageMapper.selectList(new LambdaQueryWrapper<MailMessage>()
                        .in(MailMessage::getId, pageIds)
                        .select(MailMessage::getId, MailMessage::getFolderId, MailMessage::getSubject,
                                MailMessage::getFromAddr, MailMessage::getFromName, MailMessage::getToAddrs,
                                MailMessage::getCcAddrs, MailMessage::getBccAddrs, MailMessage::getSentTime,
                                MailMessage::getSizeBytes, MailMessage::getHasAttachment, MailMessage::getSeen,
                                MailMessage::getFlagged, MailMessage::getPreview))
                .stream()
                .collect(Collectors.toMap(MailMessage::getId, message -> message, (a, b) -> a));
        Map<Long, String> folderCodes = resolveFolderCodes(accountId, null);
        // 按检索结果的顺序重排：数据库不保证 IN 查询的返回顺序，直接映射会丢掉相关度排序
        List<MailMessageVO> records = pageIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(message -> toVo(message, folderCodes.get(message.getFolderId()), List.of()))
                .toList();
        return PageResult.of(records, total, current, size);
    }

    /**
     * 对检索命中集合应用筛选条件，保留相关度顺序。
     *
     * <p>只查 ID 列：命中可能有上千条，把实体拉进内存只为过滤几个标志位不值得。
     * 无筛选条件时直接返回原列表，不加任何查询。</p>
     *
     * @param request 查询条件
     * @param ids 检索命中的邮件ID（相关度降序）
     * @return 过滤后的ID列表
     */
    private List<Long> applySearchFilters(MailMessageQueryRequest request, List<Long> ids) {
        boolean filtered = request.getSeen() != null || request.getHasAttachment() != null
                || request.getStartTime() != null || request.getEndTime() != null
                || StringUtils.hasText(request.getFromAddr());
        if (!filtered) {
            return ids;
        }
        Set<Long> matched = messageMapper.selectList(new LambdaQueryWrapper<MailMessage>()
                        .in(MailMessage::getId, ids)
                        .select(MailMessage::getId)
                        .eq(request.getSeen() != null, MailMessage::getSeen, request.getSeen())
                        .eq(request.getHasAttachment() != null, MailMessage::getHasAttachment,
                                request.getHasAttachment())
                        .eq(StringUtils.hasText(request.getFromAddr()), MailMessage::getFromAddr,
                                request.getFromAddr())
                        .ge(request.getStartTime() != null, MailMessage::getSentTime, request.getStartTime())
                        .le(request.getEndTime() != null, MailMessage::getSentTime, request.getEndTime()))
                .stream()
                .map(MailMessage::getId)
                .collect(Collectors.toSet());
        return ids.stream().filter(matched::contains).toList();
    }

    /**
     * 校验邮件归属
     *
     * @param id 邮件ID
     * @param userId 当前用户ID
     * @return 邮件实体
     */
    private MailMessage requireMessage(Long id, Long userId) {        MailMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "mail.message.not-found", "邮件不存在");
        }
        accountService.requireOwned(message.getAccountId(), userId);
        return message;
    }

    /**
     * 取邮件实际所在的 IMAP 文件夹
     *
     * <p>老的同步记录可能没有 {@code imapFolderId}（迁移前写入），此时回落到 {@code folderId}：
     * 那种记录从未被规则归类过，两者本来就相等。</p>
     *
     * @param message 邮件
     * @return 文件夹实体；无法确定返回 {@code null}
     */
    private MailFolder imapFolder(MailMessage message) {
        Long folderId = message.getImapFolderId() == null ? message.getFolderId() : message.getImapFolderId();
        return folderId == null ? null : folderMapper.selectById(folderId);
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
        // 正文回填后要同步索引：Lucene 引擎下正文只在写入索引时才可搜，
        // 而正文是懒加载的——不回填索引就会出现「打开了详情、却搜不到正文里的词」
        searchService.index(message);
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
