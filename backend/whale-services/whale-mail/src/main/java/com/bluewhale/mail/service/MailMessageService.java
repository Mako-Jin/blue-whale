package com.bluewhale.mail.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.dto.MailMessageQueryRequest;
import com.bluewhale.mail.vo.MailMessageVO;
import com.bluewhale.mail.vo.MailUnreadVO;

/**
 * 邮件查询与状态服务。
 *
 * <p>本服务只读索引并维护标记状态，不负责收信（见 {@link MailSyncService}）
 * 与发信（见 {@link MailSendService}）。</p>
 *
 * @author BlueWhale
 */
public interface MailMessageService {

    /**
     * 分页查询邮件
     *
     * @param request 查询条件
     * @param userId 当前用户ID
     * @return 分页结果
     */
    PageResult<MailMessageVO> page(MailMessageQueryRequest request, Long userId);

    /**
     * 邮件详情（含附件；正文缺失时按需回源）
     *
     * @param id 邮件ID
     * @param userId 当前用户ID
     * @return 邮件详情
     */
    MailMessageVO detail(Long id, Long userId);

    /**
     * 标记已读/未读
     *
     * @param id 邮件ID
     * @param seen 1 已读 / 0 未读
     * @param userId 当前用户ID
     */
    void markSeen(Long id, Integer seen, Long userId);

    /**
     * 标记星标
     *
     * @param id 邮件ID
     * @param flagged 1 星标 / 0 取消
     * @param userId 当前用户ID
     */
    void markFlagged(Long id, Integer flagged, Long userId);

    /**
     * 移入回收站
     *
     * @param id 邮件ID
     * @param userId 当前用户ID
     */
    void moveToTrash(Long id, Long userId);

    /**
     * 未读统计
     *
     * @param userId 当前用户ID
     * @return 未读数
     */
    MailUnreadVO unread(Long userId);
}
