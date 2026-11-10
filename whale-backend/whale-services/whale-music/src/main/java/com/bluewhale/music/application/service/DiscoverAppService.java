package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.SearchCommand;
import com.bluewhale.music.application.view.HomeView;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.application.view.SearchResultView;

/**
 * 发现与榜单应用服务（首页聚合、热门 / 新歌榜单、站内搜索）。
 *
 * @author BlueWhale
 */
public interface DiscoverAppService {

    /**
     * 首页聚合：热门歌曲、新歌、官方歌单与热门标签（走租户级 Redis 缓存）。
     *
     * @param userId 当前用户 ID
     * @return 首页聚合视图
     */
    HomeView home(Long userId);

    /**
     * 热门榜单：仅上架且公开的歌曲，按播放次数与收藏数倒序。
     *
     * @param current 页码
     * @param size 每页条数
     * @return 歌曲分页结果
     */
    PageResult<PublicSongView> hotChart(long current, long size);

    /**
     * 新歌榜单：仅上架且公开的歌曲，按上架时间倒序。
     *
     * @param current 页码
     * @param size 每页条数
     * @return 歌曲分页结果
     */
    PageResult<PublicSongView> newChart(long current, long size);

    /**
     * 站内搜索：关键词为空时返回稳定空结果，不抛错。
     *
     * @param command 搜索命令
     * @param viewerId 查看者用户 ID
     * @return 搜索结果
     */
    SearchResultView search(SearchCommand command, Long viewerId);
}
