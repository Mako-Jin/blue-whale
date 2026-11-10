package com.bluewhale.video.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.PlayProgressCommand;
import com.bluewhale.video.application.view.PlayTicketBatchView;
import com.bluewhale.video.application.view.PlayTicketView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.VideoStreamView;
import com.bluewhale.video.application.view.WatchHistoryView;

import java.util.List;

/**
 * 播放与互动应用服务：票据、Range 流式、进度、点赞与收藏。
 *
 * @author BlueWhale
 */
public interface PlayAppService {

    /**
     * 签发单个内容的播放票据。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     * @param tenantId 当前租户 ID
     * @return 票据视图
     */
    PlayTicketView issueTicket(Long contentId, Long userId, Long tenantId);

    /**
     * 批量签发播放票据（feed / 列表预取，不可播内容跳过）。
     *
     * @param contentIds 内容 ID 列表
     * @param userId 当前用户 ID
     * @param tenantId 当前租户 ID
     * @return 批量票据视图
     */
    PlayTicketBatchView issueTickets(List<Long> contentIds, Long userId, Long tenantId);

    /**
     * 打开视频流：校验票据与可播状态，透传 Range。
     *
     * @param contentId 内容 ID
     * @param ticket 票据
     * @param userId 当前用户 ID
     * @param tenantId 当前租户 ID
     * @param rangeHeader 请求 {@code Range}
     * @param ifRangeHeader 请求 {@code If-Range}
     * @return 流视图
     */
    VideoStreamView openStream(Long contentId, String ticket, Long userId, Long tenantId,
                               String rangeHeader, String ifRangeHeader);

    /**
     * 打开图片内容流（封面 / 头像代理，透传 Range）。
     *
     * @param fileId 图片文件 ID
     * @param tenantId 当前租户 ID
     * @param rangeHeader 请求 {@code Range}
     * @param ifRangeHeader 请求 {@code If-Range}
     * @return 流视图
     */
    VideoStreamView openImage(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader);

    /**
     * 上报观看进度（幂等，按用户 + 内容唯一）。
     *
     * @param command 进度命令
     * @param userId 当前用户 ID
     */
    void reportProgress(PlayProgressCommand command, Long userId);

    /**
     * 最近观看分页。
     *
     * @param userId 当前用户 ID
     * @param current 当前页
     * @param size 每页条数
     * @return 历史分页
     */
    PageResult<WatchHistoryView> recentHistory(Long userId, long current, long size);

    /**
     * 删除本人对指定内容的观看记录（幂等）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     */
    void removeHistory(Long contentId, Long userId);

    /**
     * 点赞内容（幂等）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     */
    void like(Long contentId, Long userId);

    /**
     * 取消点赞。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     */
    void unlike(Long contentId, Long userId);

    /**
     * 收藏内容（幂等）。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     */
    void favorite(Long contentId, Long userId);

    /**
     * 取消收藏。
     *
     * @param contentId 内容 ID
     * @param userId 当前用户 ID
     */
    void unfavorite(Long contentId, Long userId);

    /**
     * 我的点赞分页。
     *
     * @param userId 当前用户 ID
     * @param current 当前页
     * @param size 每页条数
     * @return 用户侧分页
     */
    PageResult<PublicContentView> myLikes(Long userId, long current, long size);

    /**
     * 我的收藏分页。
     *
     * @param userId 当前用户 ID
     * @param current 当前页
     * @param size 每页条数
     * @return 用户侧分页
     */
    PageResult<PublicContentView> myFavorites(Long userId, long current, long size);
}
