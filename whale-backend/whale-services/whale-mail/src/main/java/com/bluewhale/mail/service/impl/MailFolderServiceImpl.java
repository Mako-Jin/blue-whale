package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.enums.MailFolderType;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.mapper.MailFolderMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.support.MailImapSupport;
import com.bluewhale.mail.support.RemoteFolder;
import com.bluewhale.mail.vo.MailFolderSyncVO;
import com.bluewhale.mail.vo.MailFolderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 邮箱文件夹服务实现。
 *
 * <p>文件夹有两个来源：本地初始化（系统五件套）与服务器探测（用户自建）。两者必须持续对齐，
 * 否则用户在 Webmail 新建的文件夹永远进不了平台，反过来服务器删掉的文件夹会一直留在界面上。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFolderServiceImpl implements MailFolderService {

    /** 系统文件夹默认集合（顺序即侧边栏顺序） */
    private static final List<MailFolderType> SYSTEM_FOLDERS = List.of(
            MailFolderType.INBOX, MailFolderType.DRAFT, MailFolderType.SENT,
            MailFolderType.JUNK, MailFolderType.TRASH);

    /** 自定义文件夹类型值 */
    private static final String FOLDER_TYPE_CUSTOM = "CUSTOM";

    /** 展示名最大长度（与 mail_folder.name 一致） */
    private static final int NAME_MAX_LENGTH = 96;

    /** IMAP 名最大长度（与 mail_folder.imap_name 一致） */
    private static final int IMAP_NAME_MAX_LENGTH = 190;

    /** 下线文件夹的名称前缀：加在原名前释放唯一键，同名文件夹重建时才不会撞 */
    private static final String RETIRED_PREFIX = "~del-";

    private final MailFolderMapper folderMapper;

    private final MailMessageMapper messageMapper;

    private final MailAccountMapper accountMapper;

    private final MailImapSupport imapSupport;

    @Override
    public List<MailFolderVO> list(Long accountId, Long userId) {
        assertOwned(accountId, userId);
        return selectByAccount(accountId).stream().map(MailFolderServiceImpl::toVo).toList();
    }

    @Override
    public void ensureSystemFolders(MailAccount account) {
        for (MailFolderType type : SYSTEM_FOLDERS) {
            MailFolder folder = new MailFolder();
            folder.setAccountId(account.getId());
            folder.setFolderCode(type.code());
            folder.setName(type.displayName());
            folder.setImapName(StringUtils.hasText(type.defaultImapName()) ? type.defaultImapName() : type.code());
            folder.setFolderType(type.isSystem() ? "SYSTEM" : "CUSTOM");
            folder.setUnreadCount(0);
            folder.setTotalCount(0);
            folder.setLastUid(0L);
            folder.setSortNo(type.sortNo());
            try {
                folderMapper.insert(folder);
            } catch (DuplicateKeyException e) {
                // 重复初始化是正常路径（重试、并发绑定），忽略即可
                log.debug("系统文件夹已存在，跳过初始化: accountId={}, code={}", account.getId(), type.code());
            }
        }
    }

    @Override
    public MailFolderSyncVO syncFromServer(MailAccount account) {
        List<RemoteFolder> remote = imapSupport.listFolders(account).stream()
                .filter(RemoteFolder::selectable)
                .toList();
        List<MailFolder> local = selectByAccount(account.getId());
        MailFolderSyncVO result = new MailFolderSyncVO();
        result.setRemote(remote.size());

        Set<String> claimed = new HashSet<>();
        claimSystemFolders(account, local, remote, claimed, result);
        adoptCustomFolders(account, local, remote, claimed, result);
        result.setRemoved(retireVanished(local, remote));
        result.setTotal(selectByAccount(account.getId()).size());
        return result;
    }

    /**
     * 把系统文件夹的 IMAP 名称纠正为服务器上的真实名称
     *
     * <p>纠正后必须清空位点：不同文件夹的 UID 空间互不相干，沿用旧位点会让新映射的文件夹
     * 「从中间开始同步」，历史邮件永久缺失。这是本方法唯一容易出错的地方。</p>
     *
     * @param account 账号
     * @param local 本地文件夹
     * @param remote 服务器文件夹
     * @param claimed 已认领的 IMAP 名称（出参）
     * @param result 统计结果（出参）
     */
    private void claimSystemFolders(MailAccount account, List<MailFolder> local, List<RemoteFolder> remote,
                                    Set<String> claimed, MailFolderSyncVO result) {
        Map<String, MailFolder> byCode = new HashMap<>();
        for (MailFolder folder : local) {
            byCode.put(folder.getFolderCode(), folder);
        }
        for (MailFolderType type : SYSTEM_FOLDERS) {
            MailFolder folder = byCode.get(type.code());
            if (folder == null) {
                continue;
            }
            RemoteFolder match = pickSystemCandidate(remote, type, claimed, folder.getImapName());
            if (match == null) {
                continue;
            }
            claimed.add(match.imapName());
            if (Objects.equals(match.imapName(), folder.getImapName())) {
                continue;
            }
            log.info("系统文件夹 IMAP 名称已纠正: accountId={}, code={}, {} -> {}",
                    account.getId(), type.code(), folder.getImapName(), match.imapName());
            folder.setImapName(match.imapName());
            folder.setName(type.displayName());
            folder.setLastUid(0L);
            folder.setUidValidity(null);
            folderMapper.updateById(folder);
            result.setUpdated(result.getUpdated() + 1);
        }
    }

    /**
     * 把服务器上的自定义文件夹接入本地索引
     *
     * <p>排序号接在系统文件夹之后递增；已存在的只同步展示名，不动位点。</p>
     *
     * @param account 账号
     * @param local 本地文件夹
     * @param remote 服务器文件夹
     * @param claimed 已认领的 IMAP 名称（入参/出参）
     * @param result 统计结果（出参）
     */
    private void adoptCustomFolders(MailAccount account, List<MailFolder> local, List<RemoteFolder> remote,
                                    Set<String> claimed, MailFolderSyncVO result) {
        Map<String, MailFolder> byImap = new HashMap<>();
        int nextSort = MailFolderType.CUSTOM.sortNo();
        for (MailFolder folder : local) {
            byImap.put(folder.getImapName(), folder);
            int sortNo = folder.getSortNo() == null ? 0 : folder.getSortNo();
            nextSort = Math.max(nextSort, sortNo + 1);
        }
        for (RemoteFolder candidate : remote) {
            if (claimed.contains(candidate.imapName())) {
                continue;
            }
            String name = displayNameOf(candidate.imapName(), candidate.delimiter());
            MailFolder existing = byImap.get(candidate.imapName());
            if (existing != null) {
                if (!Objects.equals(name, existing.getName())) {
                    MailFolder patch = new MailFolder();
                    patch.setId(existing.getId());
                    patch.setName(name);
                    folderMapper.updateById(patch);
                    result.setUpdated(result.getUpdated() + 1);
                }
                continue;
            }
            MailFolder folder = buildCustomFolder(account, candidate, name, nextSort++);
            try {
                folderMapper.insert(folder);
                result.setAdded(result.getAdded() + 1);
            } catch (DuplicateKeyException e) {
                // 并发探测（定时任务与手动刷新同时跑）会撞唯一键，说明别人已插入，跳过即可
                log.debug("自定义文件夹已被并发接入: accountId={}, imapName={}",
                        account.getId(), candidate.imapName());
            }
        }
    }

    /**
     * 下线服务器上已消失的自定义文件夹
     *
     * <p>系统文件夹不在此列：服务器短时不可用时探测结果为空，若一并下线，用户会看到
     * 「收件箱不见了」这种比同步失败严重得多的故障。</p>
     *
     * <p>下线时把 {@code imap_name} 改名并置删除标记，一条语句完成：唯一键
     * {@code uk_tenant_account_imap} 不看 {@code deleted} 列，不释放名字的话，
     * 用户在服务器上重建同名文件夹会插入失败。</p>
     *
     * @param local 本地文件夹
     * @param remote 服务器文件夹
     * @return 下线数量
     */
    private int retireVanished(List<MailFolder> local, List<RemoteFolder> remote) {
        Set<String> remoteNames = new HashSet<>();
        for (RemoteFolder folder : remote) {
            remoteNames.add(folder.imapName());
        }
        int removed = 0;
        for (MailFolder folder : local) {
            if (remoteNames.contains(folder.getImapName()) || !FOLDER_TYPE_CUSTOM.equals(folder.getFolderType())) {
                continue;
            }
            folderMapper.update(null, new LambdaUpdateWrapper<MailFolder>()
                    .eq(MailFolder::getId, folder.getId())
                    .set(MailFolder::getImapName, retiredImapName(folder))
                    .set(MailFolder::getDeleted, 1));
            log.info("自定义文件夹已下线: accountId={}, imapName={}", folder.getAccountId(), folder.getImapName());
            removed++;
        }
        return removed;
    }

    /**
     * 为系统类型挑选服务器上的对应文件夹
     *
     * <p>三级优先，顺序不能颠倒：</p>
     * <ol>
     *   <li><b>沿用现有映射</b>：本地已指向的文件夹仍然存在就继续用它，避免每次探测都改写。</li>
     *   <li><b>默认名优先</b>：{@code Junk} 比别名 {@code Spam} 更像垃圾箱；用户完全可能
     *       自己建一个叫 {@code Spam} 的普通文件夹，按遍历顺序取会把它错认成系统文件夹。</li>
     *   <li><b>顶层优先于嵌套</b>：归档目录里的同名子文件夹不是系统文件夹。</li>
     * </ol>
     *
     * @param remote 服务器文件夹
     * @param type 系统类型
     * @param claimed 已认领名称
     * @param currentImapName 本地当前映射的 IMAP 名称，可为 {@code null}
     * @return 候选；无匹配返回 {@code null}
     */
    private RemoteFolder pickSystemCandidate(List<RemoteFolder> remote, MailFolderType type, Set<String> claimed,
                                             String currentImapName) {
        RemoteFolder topLevel = null;
        RemoteFolder nested = null;
        for (RemoteFolder candidate : remote) {
            if (claimed.contains(candidate.imapName())) {
                continue;
            }
            if (MailFolderType.resolveByImapName(candidate.imapName(), candidate.delimiter()) != type) {
                continue;
            }
            if (candidate.imapName().equals(currentImapName) || isDefaultName(candidate, type)) {
                return candidate;
            }
            if (MailFolderType.isTopLevel(candidate.imapName(), candidate.delimiter())) {
                if (topLevel == null) {
                    topLevel = candidate;
                }
            } else if (nested == null) {
                nested = candidate;
            }
        }
        return topLevel != null ? topLevel : nested;
    }

    /**
     * 判断是否为该类型的默认 IMAP 名称
     *
     * @param candidate 服务器文件夹
     * @param type 系统类型
     * @return 名称等于默认值时返回 {@code true}
     */
    private static boolean isDefaultName(RemoteFolder candidate, MailFolderType type) {
        String defaultName = type.defaultImapName();
        return defaultName != null && defaultName.equalsIgnoreCase(candidate.imapName());
    }

    /**
     * 构建自定义文件夹实体
     *
     * @param account 账号
     * @param candidate 服务器快照
     * @param name 展示名
     * @param sortNo 排序号
     * @return 实体
     */
    private static MailFolder buildCustomFolder(MailAccount account, RemoteFolder candidate, String name, int sortNo) {
        MailFolder folder = new MailFolder();
        folder.setAccountId(account.getId());
        folder.setFolderCode(MailFolderType.CUSTOM.code());
        folder.setName(name);
        folder.setImapName(candidate.imapName());
        folder.setFolderType(FOLDER_TYPE_CUSTOM);
        folder.setUnreadCount(0);
        folder.setTotalCount(0);
        folder.setLastUid(0L);
        folder.setSortNo(sortNo);
        return folder;
    }

    /**
     * 计算展示名：去掉 {@code INBOX} 前缀，层级分隔符换成空格斜杠
     *
     * @param imapName IMAP 名称
     * @param delimiter 层级分隔符
     * @return 展示名
     */
    private static String displayNameOf(String imapName, String delimiter) {
        String sep = delimiter == null || delimiter.isEmpty() ? "/" : delimiter;
        String name = imapName;
        String prefix = MailFolderType.INBOX.defaultImapName() + sep;
        if (name.regionMatches(true, 0, prefix, 0, prefix.length()) && name.length() > prefix.length()) {
            name = name.substring(prefix.length());
        }
        name = name.replace(sep, " / ");
        if (name.isBlank()) {
            name = imapName;
        }
        return name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
    }

    /**
     * 生成下线后的 IMAP 名称
     *
     * @param folder 文件夹
     * @return 带下线标记的名称
     */
    private static String retiredImapName(MailFolder folder) {
        String suffix = RETIRED_PREFIX + folder.getId() + "-";
        String origin = folder.getImapName() == null ? "" : folder.getImapName();
        int room = IMAP_NAME_MAX_LENGTH - suffix.length();
        return suffix + (origin.length() > room ? origin.substring(0, Math.max(room, 0)) : origin);
    }

    @Override
    public MailFolder findByCode(Long accountId, String folderCode) {
        String code = StringUtils.hasText(folderCode) ? folderCode.trim().toUpperCase() : MailFolderType.INBOX.code();
        return folderMapper.selectOne(new LambdaQueryWrapper<MailFolder>()
                .eq(MailFolder::getAccountId, accountId)
                .eq(MailFolder::getFolderCode, code)
                .orderByAsc(MailFolder::getId)
                .last("LIMIT 1"));
    }

    @Override
    public void refreshCounters(Long folderId) {
        if (folderId == null) {
            return;
        }
        Long total = messageMapper.selectCount(new LambdaQueryWrapper<MailMessage>()
                .eq(MailMessage::getFolderId, folderId));
        Long unread = messageMapper.selectCount(new LambdaQueryWrapper<MailMessage>()
                .eq(MailMessage::getFolderId, folderId)
                .eq(MailMessage::getSeen, 0));
        MailFolder update = new MailFolder();
        update.setId(folderId);
        update.setTotalCount(total == null ? 0 : total.intValue());
        update.setUnreadCount(unread == null ? 0 : unread.intValue());
        folderMapper.updateById(update);
    }

    /**
     * 查询账号下全部文件夹
     *
     * @param accountId 账号ID
     * @return 文件夹列表
     */
    private List<MailFolder> selectByAccount(Long accountId) {
        return folderMapper.selectList(new LambdaQueryWrapper<MailFolder>()
                .eq(MailFolder::getAccountId, accountId)
                .orderByAsc(MailFolder::getSortNo)
                .orderByAsc(MailFolder::getId));
    }

    /**
     * 校验账号归属
     *
     * @param accountId 账号ID
     * @param userId 当前用户ID
     */
    private void assertOwned(Long accountId, Long userId) {
        MailAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "mail.account.not-found", "邮箱账号不存在");
        }
        if (!Objects.equals(account.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "mail.account.forbidden", "无权访问该邮箱账号");
        }
    }

    /**
     * 实体转视图
     *
     * @param folder 文件夹实体
     * @return 文件夹视图
     */
    private static MailFolderVO toVo(MailFolder folder) {
        MailFolderVO vo = new MailFolderVO();
        vo.setId(folder.getId());
        vo.setFolderCode(folder.getFolderCode());
        vo.setName(folder.getName());
        vo.setFolderType(folder.getFolderType());
        vo.setUnreadCount(folder.getUnreadCount());
        vo.setTotalCount(folder.getTotalCount());
        return vo;
    }
}
