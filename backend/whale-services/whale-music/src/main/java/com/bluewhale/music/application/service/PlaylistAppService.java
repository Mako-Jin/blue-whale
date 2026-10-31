package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.AddPlaylistSongCommand;
import com.bluewhale.music.application.command.PlaylistQueryCommand;
import com.bluewhale.music.application.command.PlaylistSaveCommand;
import com.bluewhale.music.application.command.ReorderPlaylistCommand;
import com.bluewhale.music.application.view.PlaylistView;

/**
 * 歌单应用服务（用户歌单与官方歌单）。
 *
 * @author BlueWhale
 */
public interface PlaylistAppService {

    /**
     * 创建用户歌单，所有者固定为当前用户。
     *
     * @param command 歌单保存命令
     * @param userId 当前用户 ID
     * @return 歌单视图
     */
    PlaylistView create(PlaylistSaveCommand command, Long userId);

    /**
     * 修改用户歌单，仅所有者可操作。
     *
     * @param id 歌单 ID
     * @param command 歌单保存命令
     * @param userId 当前用户 ID
     * @return 歌单视图
     */
    PlaylistView update(Long id, PlaylistSaveCommand command, Long userId);

    /**
     * 逻辑删除用户歌单，仅所有者可操作。
     *
     * @param id 歌单 ID
     * @param userId 当前用户 ID
     */
    void delete(Long id, Long userId);

    /**
     * 用户歌单分页：可见公开歌单（含官方）与本人私有歌单。
     *
     * @param command 查询命令
     * @param userId 当前用户 ID
     * @return 歌单分页结果
     */
    PageResult<PlaylistView> page(PlaylistQueryCommand command, Long userId);

    /**
     * 歌单详情：私有歌单非所有者按不存在处理。
     *
     * @param id 歌单 ID
     * @param viewerId 查看者用户 ID
     * @return 歌单详情（含歌曲）
     */
    PlaylistView detail(Long id, Long viewerId);

    /**
     * 向用户歌单加歌，仅所有者可操作；重复加入幂等。
     *
     * @param id 歌单 ID
     * @param command 加歌命令
     * @param userId 当前用户 ID
     * @return 歌单详情
     */
    PlaylistView addSong(Long id, AddPlaylistSongCommand command, Long userId);

    /**
     * 从用户歌单移除歌曲，仅所有者可操作；不存在时幂等。
     *
     * @param id 歌单 ID
     * @param songId 歌曲 ID
     * @param userId 当前用户 ID
     */
    void removeSong(Long id, Long songId, Long userId);

    /**
     * 重排用户歌单歌曲，仅所有者可操作。
     *
     * @param id 歌单 ID
     * @param command 重排命令
     * @param userId 当前用户 ID
     * @return 歌单详情
     */
    PlaylistView reorder(Long id, ReorderPlaylistCommand command, Long userId);

    /**
     * 创建官方歌单：所有者固定为 0、类型 OFFICIAL、可见性固定 PUBLIC。
     *
     * @param command 歌单保存命令
     * @return 歌单视图
     */
    PlaylistView createOfficial(PlaylistSaveCommand command);

    /**
     * 修改官方歌单。
     *
     * @param id 歌单 ID
     * @param command 歌单保存命令
     * @return 歌单视图
     */
    PlaylistView updateOfficial(Long id, PlaylistSaveCommand command);

    /**
     * 逻辑删除官方歌单。
     *
     * @param id 歌单 ID
     */
    void deleteOfficial(Long id);

    /**
     * 官方歌单分页。
     *
     * @param command 查询命令
     * @return 歌单分页结果
     */
    PageResult<PlaylistView> officialPage(PlaylistQueryCommand command);

    /**
     * 运营视角向歌单加歌：官方歌单可直接维护，用户歌单仍需所有者本人。
     *
     * @param id 歌单 ID
     * @param command 加歌命令
     * @param operatorId 运营用户 ID
     * @return 歌单详情
     */
    PlaylistView addSongAsOperator(Long id, AddPlaylistSongCommand command, Long operatorId);

    /**
     * 运营视角从歌单移除歌曲：官方歌单可直接维护，用户歌单仍需所有者本人。
     *
     * @param id 歌单 ID
     * @param songId 歌曲 ID
     * @param operatorId 运营用户 ID
     */
    void removeSongAsOperator(Long id, Long songId, Long operatorId);

    /**
     * 运营视角重排歌单歌曲：官方歌单可直接维护，用户歌单仍需所有者本人。
     *
     * @param id 歌单 ID
     * @param command 重排命令
     * @param operatorId 运营用户 ID
     * @return 歌单详情
     */
    PlaylistView reorderAsOperator(Long id, ReorderPlaylistCommand command, Long operatorId);
}
