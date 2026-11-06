package com.bluewhale.mail.service;

import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.vo.MailFolderSyncVO;
import com.bluewhale.mail.vo.MailFolderVO;

import java.util.List;

/**
 * 邮箱文件夹服务。
 *
 * @author BlueWhale
 */
public interface MailFolderService {

    /**
     * 列出账号下的文件夹（含未读数）
     *
     * @param accountId 账号ID
     * @param userId 当前用户ID
     * @return 文件夹列表
     */
    List<MailFolderVO> list(Long accountId, Long userId);

    /**
     * 初始化系统文件夹（幂等）
     *
     * @param account 邮箱账号
     */
    void ensureSystemFolders(MailAccount account);

    /**
     * 探测服务器文件夹并与本地对齐。
     *
     * <p>三件事：把系统文件夹的 IMAP 名称纠正为服务器上的真实名称（{@code Sent Items} 之类）、
     * 把用户新建的自定义文件夹接入索引、把服务器上已消失的自定义文件夹下线。
     * 系统文件夹即使探测不到也保留——服务器短时不可用不该导致「已发送」从界面消失。</p>
     *
     * @param account 邮箱账号
     * @return 对齐结果统计
     */
    MailFolderSyncVO syncFromServer(MailAccount account);

    /**
     * 按编码获取文件夹
     *
     * @param accountId 账号ID
     * @param folderCode 文件夹编码
     * @return 文件夹实体；不存在返回 {@code null}
     */
    MailFolder findByCode(Long accountId, String folderCode);

    /**
     * 重算文件夹计数（未读与总数）
     *
     * @param folderId 文件夹ID
     */
    void refreshCounters(Long folderId);
}
