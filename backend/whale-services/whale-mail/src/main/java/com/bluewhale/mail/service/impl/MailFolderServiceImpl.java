package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.bluewhale.mail.vo.MailFolderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 邮箱文件夹服务实现。
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

    private final MailFolderMapper folderMapper;

    private final MailMessageMapper messageMapper;

    private final MailAccountMapper accountMapper;

    @Override
    public List<MailFolderVO> list(Long accountId, Long userId) {
        assertOwned(accountId, userId);
        List<MailFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<MailFolder>()
                .eq(MailFolder::getAccountId, accountId)
                .orderByAsc(MailFolder::getSortNo)
                .orderByAsc(MailFolder::getId));
        return folders.stream().map(MailFolderServiceImpl::toVo).toList();
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
     * 校验账号归属
     *
     * @param accountId 账号ID
     * @param userId 当前用户ID
     */
    private void assertOwned(Long accountId, Long userId) {
        MailAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "邮箱账号不存在");
        }
        if (!Objects.equals(account.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该邮箱账号");
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
