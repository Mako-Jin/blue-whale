package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.mapper.MailFolderMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.service.MailSyncService;
import com.bluewhale.mail.support.FetchedMail;
import com.bluewhale.mail.support.FetchResult;
import com.bluewhale.mail.support.MailImapSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 收信同步服务实现。
 *
 * <p>同步是幂等的：以 {@code (account, folder, uid)} 为自然键，已存在则只刷新已读/星标状态；
 * 本地发出的副本（UID 为负）按 Message-ID 去重，避免与服务器回流的同一封邮件重复。</p>
 *
 * <p>并发保护用进程内同步块：同步是有状态的长任务，单实例串行足够；多实例部署时
 * 需换成分布式锁（Redisson 已在 starter 中），见类尾注。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSyncServiceImpl implements MailSyncService {

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 系统文件夹类型 */
    private static final String FOLDER_TYPE_SYSTEM = "SYSTEM";

    private final MailAccountMapper accountMapper;

    private final MailFolderMapper folderMapper;

    private final MailMessageMapper messageMapper;

    private final MailAccountService accountService;

    private final MailFolderService folderService;

    private final MailImapSupport imapSupport;

    private final MailProperties properties;

    @Override
    public int syncAccount(Long accountId, Long userId) {
        MailAccount account = accountService.requireOwned(accountId, userId);
        return doSync(account);
    }

    @Override
    public int syncAll() {
        List<MailAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getStatus, STATUS_ENABLED)
                .eq(MailAccount::getSyncEnabled, 1));
        int total = 0;
        for (MailAccount account : accounts) {
            try {
                total += doSync(account);
            } catch (Exception e) {
                // 单账号失败不影响其余账号；连续失败应由监控告警而非重试风暴兜底
                log.warn("邮箱同步失败: accountId={}, reason={}", account.getId(), e.getMessage());
            }
        }
        return total;
    }

    /**
     * 定时触发全量账号同步
     */
    @Scheduled(fixedDelayString = "${whale.mail.sync.fixed-delay-millis:300000}", initialDelay = 60_000)
    public void scheduledSync() {
        if (!properties.isSyncEnabled()) {
            return;
        }
        int added = syncAll();
        if (added > 0) {
            log.info("定时邮箱同步完成，新增邮件 {} 封", added);
        }
    }

    /**
     * 同步单个账号
     *
     * @param account 账号
     * @return 新增邮件数
     */
    private synchronized int doSync(MailAccount account) {
        List<MailFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<MailFolder>()
                .eq(MailFolder::getAccountId, account.getId())
                .orderByAsc(MailFolder::getSortNo));
        int added = 0;
        for (MailFolder folder : folders) {
            if (!FOLDER_TYPE_SYSTEM.equals(folder.getFolderType())) {
                // M0 只同步系统文件夹；自定义文件夹由用户显式订阅后再纳入
                continue;
            }
            FetchResult result;
            try {
                result = imapSupport.fetchSince(account, folder, properties.getMaxMessagesPerSync());
            } catch (Exception e) {
                log.warn("文件夹同步失败: accountId={}, folder={}, reason={}",
                        account.getId(), folder.getFolderCode(), e.getMessage());
                continue;
            }
            added += persist(account, folder, result);
        }
        account.setLastSyncTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return added;
    }

    /**
     * 落库单文件夹的拉取结果
     *
     * @param account 账号
     * @param folder 文件夹
     * @param result 拉取结果
     * @return 新增邮件数
     */
    private int persist(MailAccount account, MailFolder folder, FetchResult result) {
        long uidValidity = result.uidValidity();
        if (uidValidity > 0 && folder.getUidValidity() != null && folder.getUidValidity() != uidValidity) {
            // UIDVALIDITY 变化意味着服务器侧 UID 空间已重建，旧位点不可信。
            // M0 不自动清库（会与逻辑删除的唯一键冲突），只重置位点并告警，由运维决定是否重建索引。
            log.warn("IMAP UIDVALIDITY 已变化，需重建索引: accountId={}, folder={}, old={}, new={}",
                    account.getId(), folder.getFolderCode(), folder.getUidValidity(), uidValidity);
            folder.setLastUid(0L);
        }
        long maxUid = folder.getLastUid() == null ? 0L : folder.getLastUid();
        int added = 0;
        for (FetchedMail mail : result.mails()) {
            maxUid = Math.max(maxUid, mail.uid());
            MailMessage existing = findByUid(account.getId(), folder.getId(), mail.uid());
            if (existing != null) {
                syncFlags(existing, mail);
                continue;
            }
            if (isDuplicateByMessageId(account.getId(), mail.messageId())) {
                // 本地发出的副本已占用同一 Message-ID
                continue;
            }
            messageMapper.insert(toEntity(account, folder, mail));
            added++;
        }
        folder.setLastUid(maxUid);
        if (uidValidity > 0) {
            folder.setUidValidity(uidValidity);
        }
        folderMapper.updateById(folder);
        folderService.refreshCounters(folder.getId());
        return added;
    }

    /**
     * 按 UID 查已有索引
     *
     * @param accountId 账号ID
     * @param folderId 文件夹ID
     * @param uid UID
     * @return 邮件；不存在返回 {@code null}
     */
    private MailMessage findByUid(Long accountId, Long folderId, long uid) {
        List<MailMessage> found = messageMapper.selectList(new LambdaQueryWrapper<MailMessage>()
                .eq(MailMessage::getAccountId, accountId)
                .eq(MailMessage::getFolderId, folderId)
                .eq(MailMessage::getUid, uid)
                .last("LIMIT 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 判断同账号下是否已存在相同 Message-ID
     *
     * @param accountId 账号ID
     * @param messageId Message-ID
     * @return 存在返回 {@code true}
     */
    private boolean isDuplicateByMessageId(Long accountId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<MailMessage>()
                .eq(MailMessage::getAccountId, accountId)
                .eq(MailMessage::getMessageId, messageId));
        return count != null && count > 0;
    }

    /**
     * 刷新已有邮件的状态标记
     *
     * @param existing 已有索引
     * @param mail 服务器快照
     */
    private void syncFlags(MailMessage existing, FetchedMail mail) {
        int seen = mail.seen() ? 1 : 0;
        int flagged = mail.flagged() ? 1 : 0;
        if (Objects.equals(existing.getSeen(), seen) && Objects.equals(existing.getFlagged(), flagged)) {
            return;
        }
        existing.setSeen(seen);
        existing.setFlagged(flagged);
        messageMapper.updateById(existing);
    }

    /**
     * 快照转实体
     *
     * @param account 账号
     * @param folder 文件夹
     * @param mail 快照
     * @return 实体
     */
    private MailMessage toEntity(MailAccount account, MailFolder folder, FetchedMail mail) {
        MailMessage message = new MailMessage();
        message.setAccountId(account.getId());
        message.setFolderId(folder.getId());
        message.setUid(mail.uid());
        message.setUidValidity(folder.getUidValidity());
        message.setMessageId(mail.messageId());
        message.setSubject(mail.subject());
        message.setFromAddr(mail.fromAddr());
        message.setFromName(mail.fromName());
        message.setToAddrs(mail.toAddrs());
        message.setCcAddrs(mail.ccAddrs());
        message.setSentTime(mail.sentTime() == null ? LocalDateTime.now() : mail.sentTime());
        message.setReceivedTime(LocalDateTime.now());
        message.setSizeBytes(mail.sizeBytes());
        message.setHasAttachment(0);
        message.setSeen(mail.seen() ? 1 : 0);
        message.setFlagged(mail.flagged() ? 1 : 0);
        message.setAnswered(0);
        message.setForwarded(0);
        message.setPriority(3);
        return message;
    }
}
