package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.AddPlaylistSongCommand;
import com.bluewhale.music.application.command.PlaylistQueryCommand;
import com.bluewhale.music.application.command.PlaylistSaveCommand;
import com.bluewhale.music.application.command.ReorderPlaylistCommand;
import com.bluewhale.music.application.service.PlaylistAppService;
import com.bluewhale.music.application.view.PlaylistSongView;
import com.bluewhale.music.application.view.PlaylistView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.Playlist;
import com.bluewhale.music.domain.model.PlaylistQuery;
import com.bluewhale.music.domain.model.PlaylistSong;
import com.bluewhale.music.domain.model.PlaylistType;
import com.bluewhale.music.domain.model.PlaylistVisibility;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.PlaylistRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 歌单应用服务实现。
 *
 * <p>用户歌单仅所有者可维护，官方歌单固定 {@code ownerId = 0} 且公开、用户侧只读；
 * 歌单内歌曲上限与去重、重排合法性统一委托 {@link Playlist} 聚合根校验。
 * 官方歌单变更后主动失效租户首页缓存。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PlaylistAppServiceImpl implements PlaylistAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 官方歌单所有者用户 ID */
    private static final Long OFFICIAL_OWNER_ID = 0L;

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 歌单初始歌曲数量 */
    private static final int DEFAULT_SONG_COUNT = 0;

    /** 歌单初始播放次数 */
    private static final long DEFAULT_PLAY_COUNT = 0L;

    private final PlaylistRepository playlistRepository;

    private final SongRepository songRepository;

    private final MusicProperties musicProperties;

    private final HomeCacheRepository homeCacheRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView create(PlaylistSaveCommand command, Long userId) {
        Playlist playlist = new Playlist();
        playlist.setOwnerId(userId);
        playlist.setPlaylistType(PlaylistType.USER);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        initCounters(playlist);
        fill(playlist, command);
        playlistRepository.insert(playlist);
        return toView(playlist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView update(Long id, PlaylistSaveCommand command, Long userId) {
        Playlist playlist = requireUserPlaylist(id, userId);
        fill(playlist, command);
        playlistRepository.update(playlist);
        return toView(playlist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Playlist playlist = requireUserPlaylist(id, userId);
        playlistRepository.delete(playlist.getId());
    }

    @Override
    public PageResult<PlaylistView> page(PlaylistQueryCommand command, Long userId) {
        PlaylistQuery query = new PlaylistQuery();
        query.setKeyword(command.getKeyword());
        query.setViewerId(userId);
        query.setOwnerId(command.isMine() ? userId : null);
        query.setCurrent(clampCurrent(command.getCurrent()));
        query.setSize(clampSize(command.getSize()));
        List<PlaylistView> records = playlistRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, playlistRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public PlaylistView detail(Long id, Long viewerId) {
        return buildDetail(requireVisible(id, viewerId), viewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView addSong(Long id, AddPlaylistSongCommand command, Long userId) {
        return addSongInternal(id, command, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSong(Long id, Long songId, Long userId) {
        removeSongInternal(id, songId, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView reorder(Long id, ReorderPlaylistCommand command, Long userId) {
        return reorderInternal(id, command, userId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView addSongAsOperator(Long id, AddPlaylistSongCommand command, Long operatorId) {
        return addSongInternal(id, command, operatorId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSongAsOperator(Long id, Long songId, Long operatorId) {
        removeSongInternal(id, songId, operatorId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView reorderAsOperator(Long id, ReorderPlaylistCommand command, Long operatorId) {
        return reorderInternal(id, command, operatorId, true);
    }

    /**
     * 加歌实现：运营可维护官方歌单，用户歌单仍走所有者校验。
     *
     * @param id 歌单 ID
     * @param command 加歌命令
     * @param userId 操作用户 ID
     * @param operator 是否为曲库运营人员
     * @return 歌单详情
     */
    private PlaylistView addSongInternal(Long id, AddPlaylistSongCommand command, Long userId,
                                         boolean operator) {
        Playlist playlist = requireEditablePlaylist(id, userId, operator);
        Song song = songRepository.findById(command.getSongId())
                .filter(item -> item.visibleTo(userId, false) && item.playable())
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
        playlist.setSongs(new ArrayList<>(playlistRepository.findSongs(id)));
        if (playlist.addSong(song.getId(), userId, musicProperties.getPlaylistMaxSongs())) {
            List<PlaylistSong> songs = playlist.getSongs();
            playlistRepository.insertSong(songs.get(songs.size() - 1));
            playlistRepository.update(playlist);
        }
        return buildDetail(playlist, userId);
    }

    /**
     * 移歌实现：运营可维护官方歌单，用户歌单仍走所有者校验。
     *
     * @param id 歌单 ID
     * @param songId 歌曲 ID
     * @param userId 操作用户 ID
     * @param operator 是否为曲库运营人员
     */
    private void removeSongInternal(Long id, Long songId, Long userId, boolean operator) {
        Playlist playlist = requireEditablePlaylist(id, userId, operator);
        playlist.setSongs(new ArrayList<>(playlistRepository.findSongs(id)));
        if (!playlist.removeSong(songId)) {
            return;
        }
        playlistRepository.deleteSong(id, songId);
        for (PlaylistSong item : playlist.getSongs()) {
            playlistRepository.updateSongSort(id, item.getSongId(), item.getSortNo());
        }
        playlistRepository.update(playlist);
    }

    /**
     * 重排实现：运营可维护官方歌单，用户歌单仍走所有者校验。
     *
     * @param id 歌单 ID
     * @param command 重排命令
     * @param userId 操作用户 ID
     * @param operator 是否为曲库运营人员
     * @return 歌单详情
     */
    private PlaylistView reorderInternal(Long id, ReorderPlaylistCommand command, Long userId,
                                         boolean operator) {
        Playlist playlist = requireEditablePlaylist(id, userId, operator);
        playlist.setSongs(new ArrayList<>(playlistRepository.findSongs(id)));
        playlist.reorder(command.getSongIds());
        for (PlaylistSong item : playlist.getSongs()) {
            playlistRepository.updateSongSort(id, item.getSongId(), item.getSortNo());
        }
        return buildDetail(playlist, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView createOfficial(PlaylistSaveCommand command) {
        Playlist playlist = new Playlist();
        playlist.setOwnerId(OFFICIAL_OWNER_ID);
        playlist.setPlaylistType(PlaylistType.OFFICIAL);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        initCounters(playlist);
        fill(playlist, command);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        playlistRepository.insert(playlist);
        evictHomeCache();
        return toView(playlist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistView updateOfficial(Long id, PlaylistSaveCommand command) {
        Playlist playlist = requireOfficial(id);
        fill(playlist, command);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        playlistRepository.update(playlist);
        evictHomeCache();
        return toView(playlist);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOfficial(Long id) {
        Playlist playlist = requireOfficial(id);
        playlistRepository.delete(playlist.getId());
        evictHomeCache();
    }

    @Override
    public PageResult<PlaylistView> officialPage(PlaylistQueryCommand command) {
        PlaylistQuery query = new PlaylistQuery();
        query.setKeyword(command.getKeyword());
        query.setPlaylistType(PlaylistType.OFFICIAL);
        query.setPublicOnly(true);
        query.setCurrent(clampCurrent(command.getCurrent()));
        query.setSize(clampSize(command.getSize()));
        List<PlaylistView> records = playlistRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, playlistRepository.count(query), query.getCurrent(), query.getSize());
    }

    /**
     * 将命令字段写入歌单（标题、简介、封面与可见性）。
     *
     * @param playlist 歌单
     * @param command 歌单保存命令
     */
    private void fill(Playlist playlist, PlaylistSaveCommand command) {
        playlist.rename(command.getTitle());
        playlist.setDescription(command.getDescription());
        playlist.setCoverFileId(command.getCoverFileId());
        if (command.getVisibility() != null) {
            playlist.setVisibility(command.getVisibility());
        }
    }

    /**
     * 初始化歌单计数与状态。
     *
     * @param playlist 歌单
     */
    private void initCounters(Playlist playlist) {
        playlist.setSongCount(DEFAULT_SONG_COUNT);
        playlist.setPlayCount(DEFAULT_PLAY_COUNT);
        playlist.setStatus(STATUS_ENABLED);
        playlist.setSongs(new ArrayList<>());
    }

    /**
     * 组装歌单详情（含用户可见歌曲与排序号）。
     *
     * @param playlist 歌单
     * @param viewerId 查看者用户 ID
     * @return 歌单详情视图
     */
    private PlaylistView buildDetail(Playlist playlist, Long viewerId) {
        List<PlaylistSong> items = playlistRepository.findSongs(playlist.getId());
        List<Long> songIds = items.stream().map(PlaylistSong::getSongId).toList();
        Map<Long, Song> songMap = songRepository.findByIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        List<PlaylistSongView> views = new ArrayList<>(items.size());
        for (PlaylistSong item : items) {
            Song song = songMap.get(item.getSongId());
            if (song != null && song.visibleTo(viewerId, false)) {
                views.add(toPlaylistSongView(song, item.getSortNo()));
            }
        }
        PlaylistView view = toView(playlist);
        view.setSongs(views);
        return view;
    }

    /**
     * 加载歌单，不存在时抛出业务异常。
     *
     * @param id 歌单 ID
     * @return 歌单
     */
    private Playlist require(Long id) {
        return playlistRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.PLAYLIST_NOT_FOUND));
    }

    /**
     * 加载当前用户可见的歌单，私有歌单非所有者按不存在处理。
     *
     * @param id 歌单 ID
     * @param viewerId 查看者用户 ID
     * @return 歌单
     */
    private Playlist requireVisible(Long id, Long viewerId) {
        Playlist playlist = require(id);
        if (!playlist.visibleTo(viewerId)) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_NOT_FOUND);
        }
        return playlist;
    }

    /**
     * 加载可编辑的用户歌单：私有歌单非所有者先按不存在处理（避免泄露存在性），
     * 官方歌单用户侧只读，公开歌单非所有者按无权限拒绝。
     *
     * @param id 歌单 ID
     * @param userId 操作用户 ID
     * @return 歌单
     */
    private Playlist requireUserPlaylist(Long id, Long userId) {
        Playlist playlist = requireVisible(id, userId);
        if (playlist.getPlaylistType() != PlaylistType.USER) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_OFFICIAL_READONLY);
        }
        playlist.assertOwnedBy(userId);
        return playlist;
    }

    /**
     * 加载可维护曲目的歌单：运营可维护官方歌单，用户歌单仍须所有者本人。
     *
     * <p>用户视角先按可见性过滤，私有歌单非所有者按不存在处理（避免泄露存在性）。</p>
     *
     * @param id 歌单 ID
     * @param userId 操作用户 ID
     * @param operator 是否为曲库运营人员
     * @return 歌单
     */
    private Playlist requireEditablePlaylist(Long id, Long userId, boolean operator) {
        Playlist playlist = operator ? require(id) : requireVisible(id, userId);
        if (playlist.getPlaylistType() == PlaylistType.OFFICIAL) {
            if (!operator) {
                throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_OFFICIAL_READONLY);
            }
            return playlist;
        }
        playlist.assertOwnedBy(userId);
        return playlist;
    }

    /**
     * 加载官方歌单，类型不匹配按不存在处理。
     *
     * @param id 歌单 ID
     * @return 歌单
     */
    private Playlist requireOfficial(Long id) {
        Playlist playlist = require(id);
        if (playlist.getPlaylistType() != PlaylistType.OFFICIAL) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_NOT_FOUND);
        }
        return playlist;
    }

    /**
     * 失效当前租户的首页缓存。
     */
    private void evictHomeCache() {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            homeCacheRepository.evict(tenantId);
        }
    }

    /**
     * 歌单领域模型转视图（列表场景不填充歌曲）。
     *
     * @param playlist 歌单
     * @return 歌单视图
     */
    private PlaylistView toView(Playlist playlist) {
        PlaylistView view = new PlaylistView();
        BeanUtils.copyProperties(playlist, view);
        view.setSongs(null);
        return view;
    }

    /**
     * 歌曲领域模型转歌单内歌曲视图（不暴露音频文件 ID）。
     *
     * @param song 歌曲
     * @param sortNo 歌单内排序号
     * @return 歌单内歌曲视图
     */
    private PlaylistSongView toPlaylistSongView(Song song, Integer sortNo) {
        PlaylistSongView view = new PlaylistSongView();
        BeanUtils.copyProperties(song, view);
        view.setPlayable(song.playable());
        view.setTags(List.of());
        view.setSortNo(sortNo);
        return view;
    }

    /**
     * 限制页码。
     *
     * @param current 请求页码
     * @return 归一化页码
     */
    private long clampCurrent(long current) {
        return Math.max(current, 1L);
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
