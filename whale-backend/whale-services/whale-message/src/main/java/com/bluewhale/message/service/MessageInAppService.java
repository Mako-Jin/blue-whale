package com.bluewhale.message.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.InAppMessageQueryRequest;
import com.bluewhale.message.vo.InAppMessageVO;

/**
 * 站内信收件箱服务。
 *
 * <p>所有方法都以网关 {@code X-User-Id} 为隔离条件，只允许读取/标记本人消息。</p>
 *
 * @author BlueWhale
 */
public interface MessageInAppService {

    /**
     * 分页查询本人站内信。
     *
     * @param userId  当前用户ID
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<InAppMessageVO> page(Long userId, InAppMessageQueryRequest request);

    /**
     * 统计本人未读站内信数量。
     *
     * @param userId 当前用户ID
     * @return 未读数量
     */
    long unreadCount(Long userId);

    /**
     * 标记本人某条站内信为已读。
     *
     * @param userId    当前用户ID
     * @param messageId 消息ID
     */
    void markRead(Long userId, Long messageId);
}
