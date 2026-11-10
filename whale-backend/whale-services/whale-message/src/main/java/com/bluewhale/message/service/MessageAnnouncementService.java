package com.bluewhale.message.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.AnnouncementPublishRequest;
import com.bluewhale.message.dto.AnnouncementQueryRequest;
import com.bluewhale.message.vo.AnnouncementVO;

/**
 * 系统公告（全员广播）服务。
 *
 * <p>公告单条发布、不枚举接收人：发布时只落一条 {@code msg_record} + {@code msg_announcement}，
 * 用户打开收件箱时由 {@code AnnouncementExpander} 按需展开为个人 INAPP 投递记录，
 * 已读状态复用投递记录 {@code read_flag}。公告面向当前租户全体用户。</p>
 *
 * @author BlueWhale
 */
public interface MessageAnnouncementService {

    /**
     * 发布一条系统公告。
     *
     * @param request     发布请求
     * @param publisherId 发布人用户ID（可为空）
     * @return 公告视图
     */
    AnnouncementVO publish(AnnouncementPublishRequest request, Long publisherId);

    /**
     * 分页查询公告。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<AnnouncementVO> page(AnnouncementQueryRequest request);

    /**
     * 撤回公告：未展开用户不再可见，已展开的个人消息保留。
     *
     * @param id 公告ID
     */
    void revoke(Long id);

    /**
     * 删除公告（逻辑删除，仅删公告行，不影响已展开的个人消息）。
     *
     * @param id 公告ID
     */
    void delete(Long id);
}
