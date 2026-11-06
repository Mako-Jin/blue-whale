package com.bluewhale.mail.service;

import com.bluewhale.mail.dto.MailAccountSaveRequest;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.vo.MailAccountVO;

import java.util.List;

/**
 * 邮箱账号服务。
 *
 * <p>所有按账号操作的方法都必须经过 {@link #requireOwned} 校验，禁止直接按 ID 读取，
 * 否则任何登录用户都能通过遍历 ID 读他人邮箱。</p>
 *
 * @author BlueWhale
 */
public interface MailAccountService {

    /**
     * 当前用户的默认邮箱账号（取第一个启用的账号）
     *
     * @param userId 当前用户ID
     * @return 账号视图；未绑定时返回 {@code null}
     */
    MailAccountVO current(Long userId);

    /**
     * 当前用户绑定的全部邮箱账号
     *
     * @param userId 当前用户ID
     * @return 账号列表
     */
    List<MailAccountVO> listByUser(Long userId);

    /**
     * 绑定邮箱账号，并初始化系统文件夹
     *
     * @param request 绑定请求
     * @param userId 当前用户ID
     * @return 创建后的账号
     */
    MailAccountVO create(MailAccountSaveRequest request, Long userId);

    /**
     * 更新邮箱账号
     *
     * @param id 账号ID
     * @param request 更新请求
     * @param userId 当前用户ID
     * @return 更新后的账号
     */
    MailAccountVO update(Long id, MailAccountSaveRequest request, Long userId);

    /**
     * 解绑邮箱账号（逻辑删除，邮件索引保留）
     *
     * @param id 账号ID
     * @param userId 当前用户ID
     */
    void remove(Long id, Long userId);

    /**
     * 校验账号归属并返回实体
     *
     * @param accountId 账号ID
     * @param userId 当前用户ID
     * @return 账号实体
     */
    MailAccount requireOwned(Long accountId, Long userId);

    /**
     * 解析当前用户的默认账号ID
     *
     * @param userId 当前用户ID
     * @return 账号ID
     */
    Long requireDefaultAccountId(Long userId);
}
