package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.PlayProgressCommand;
import com.bluewhale.music.application.view.AudioStreamView;
import com.bluewhale.music.application.view.PlayHistoryView;
import com.bluewhale.music.application.view.PlayTicketView;
import com.bluewhale.music.application.view.PublicSongView;

/**
 * 播放与音频流应用服务（M2）。
 *
 * <p>票据签发校验发布状态、版权、可见性与租户；流式读取每次校验票据三要素并
 * 复核歌曲状态，版权下架后立即拒绝；播放历史与收藏均幂等且按租户隔离。</p>
 *
 * @author BlueWhale
 */
public interface PlayAppService {

    /**
     * 签发播放票据（绑定 tenantId + userId + songId）。
     *
     * @param songId 歌曲 ID
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 票据与播放元数据
     */
    PlayTicketView issueTicket(Long songId, Long userId, Long tenantId);

    /**
     * 校验票据并读取音频内容，透传 Range。
     *
     * @param songId 歌曲 ID
     * @param ticket 票据令牌
     * @param userId 当前用户 ID
     * @param tenantId 当前租户 ID
     * @param rangeHeader 请求的 {@code Range} 头
     * @param ifRangeHeader 请求的 {@code If-Range} 头
     * @return 音频流视图
     */
    AudioStreamView openStream(Long songId, String ticket, Long userId, Long tenantId,
                               String rangeHeader, String ifRangeHeader);

    /**
     * 上报播放进度（同一用户同一歌曲重复上报幂等）。
     *
     * @param command 播放进度命令
     * @param userId 用户 ID
     */
    void reportProgress(PlayProgressCommand command, Long userId);

    /**
     * 最近播放（按播放时间倒序）。
     *
     * @param userId 用户 ID
     * @param current 页码
     * @param size 每页条数
     * @return 播放历史分页
     */
    PageResult<PlayHistoryView> recentHistory(Long userId, long current, long size);

    /**
     * 收藏歌曲（幂等）。
     *
     * @param songId 歌曲 ID
     * @param userId 用户 ID
     */
    void like(Long songId, Long userId);

    /**
     * 取消收藏（幂等）。
     *
     * @param songId 歌曲 ID
     * @param userId 用户 ID
     */
    void unlike(Long songId, Long userId);

    /**
     * 我的收藏分页（仅返回当前仍可见的歌曲）。
     *
     * @param userId 用户 ID
     * @param current 页码
     * @param size 每页条数
     * @return 用户侧歌曲分页
     */
    PageResult<PublicSongView> myLikes(Long userId, long current, long size);
}
