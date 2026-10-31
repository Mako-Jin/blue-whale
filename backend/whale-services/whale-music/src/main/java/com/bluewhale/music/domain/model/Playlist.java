package com.bluewhale.music.domain.model;

import com.bluewhale.music.domain.exception.MusicErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 歌单领域模型（聚合根）。
 *
 * <p>用户歌单仅所有者可编辑；官方歌单（{@code ownerId = 0}）用户侧只读且固定公开。
 * 单个歌单最多 {@code maxSongs} 首歌曲，重复加入幂等；重排只允许歌单内已有歌曲，
 * 缺失、多余或重复的歌曲 ID 均按参数错误拒绝。</p>
 *
 * @author BlueWhale
 */
@Data
public class Playlist {

    /** 歌单标题最大长度 */
    private static final int TITLE_MAX_LENGTH = 120;

    /** 主键 */
    private Long id;

    /** 所有者用户 ID，官方歌单为 0 */
    private Long ownerId;

    /** 标题 */
    private String title;

    /** 封面文件 ID（whale-file） */
    private Long coverFileId;

    /** 简介 */
    private String description;

    /** 歌单类型 */
    private PlaylistType playlistType;

    /** 可见性 */
    private PlaylistVisibility visibility;

    /** 歌曲数量（上限 500） */
    private Integer songCount;

    /** 播放次数 */
    private Long playCount;

    /** 状态：1 正常 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 歌单内歌曲（按 {@code sortNo} 升序） */
    private List<PlaylistSong> songs = new ArrayList<>();

    /**
     * 加入歌曲：重复加入幂等返回 {@code false}，超出上限抛业务异常。
     *
     * @param songId 歌曲 ID
     * @param addedBy 操作用户 ID
     * @param maxSongs 单歌单歌曲上限，{@code <= 0} 表示不限制
     * @return 实际新增返回 {@code true}；歌曲已在歌单内返回 {@code false}
     */
    public boolean addSong(Long songId, Long addedBy, int maxSongs) {
        if (songId == null || songId <= 0) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_SONG_INVALID);
        }
        if (findSong(songId) != null) {
            return false;
        }
        if (maxSongs > 0 && songs.size() >= maxSongs) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_SONG_LIMIT);
        }
        PlaylistSong item = new PlaylistSong();
        item.setPlaylistId(id);
        item.setSongId(songId);
        item.setSortNo(nextSortNo());
        item.setAddedBy(addedBy);
        songs.add(item);
        songCount = songs.size();
        return true;
    }

    /**
     * 移除歌曲：不存在时幂等返回 {@code false}，移除后重新编号。
     *
     * @param songId 歌曲 ID
     * @return 实际移除返回 {@code true}
     */
    public boolean removeSong(Long songId) {
        PlaylistSong target = findSong(songId);
        if (target == null) {
            return false;
        }
        songs.remove(target);
        renumber();
        songCount = songs.size();
        return true;
    }

    /**
     * 按给定顺序重排歌单内歌曲。
     *
     * <p>歌曲 ID 集合必须与歌单内已有歌曲完全一致，缺失、多余或重复 ID 均拒绝。</p>
     *
     * @param songIds 有序歌曲 ID 列表
     */
    public void reorder(List<Long> songIds) {
        if (songIds == null || songIds.size() != songs.size()
                || new HashSet<>(songIds).size() != songIds.size()
                || !new HashSet<>(songIds).equals(new HashSet<>(currentSongIds()))) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_SONG_ORDER_INVALID);
        }
        List<PlaylistSong> ordered = new ArrayList<>(songIds.size());
        for (Long songId : songIds) {
            ordered.add(findSong(songId));
        }
        songs = ordered;
        renumber();
    }

    /**
     * 重命名歌单：标题不能为空且不超过 120 个字符。
     *
     * @param title 新标题
     */
    public void rename(String title) {
        if (title == null || title.isBlank() || title.trim().length() > TITLE_MAX_LENGTH) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_TITLE_INVALID);
        }
        this.title = title.trim();
    }

    /**
     * 校验歌单归属，非所有者操作直接拒绝。
     *
     * @param userId 操作用户 ID
     */
    public void assertOwnedBy(Long userId) {
        if (userId == null || !userId.equals(ownerId)) {
            throw MusicErrorCode.exception(MusicErrorCode.PLAYLIST_NOT_OWNED);
        }
    }

    /**
     * 判断指定用户是否可见该歌单。
     *
     * @param viewerId 查看者用户 ID，可为 {@code null}
     * @return 公开歌单或所有者本人返回 {@code true}
     */
    public boolean visibleTo(Long viewerId) {
        return visibility == PlaylistVisibility.PUBLIC || (viewerId != null && viewerId.equals(ownerId));
    }

    /**
     * 按歌曲 ID 查找歌单内条目。
     *
     * @param songId 歌曲 ID
     * @return 歌单歌曲；不存在返回 {@code null}
     */
    private PlaylistSong findSong(Long songId) {
        for (PlaylistSong item : songs) {
            if (item.getSongId().equals(songId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 计算下一个排序号（当前最大排序号 + 1）。
     *
     * @return 新的排序号
     */
    private int nextSortNo() {
        int max = -1;
        for (PlaylistSong item : songs) {
            if (item.getSortNo() != null && item.getSortNo() > max) {
                max = item.getSortNo();
            }
        }
        return max + 1;
    }

    /**
     * 按当前列表顺序重新编号，保证排序号连续。
     */
    private void renumber() {
        for (int index = 0; index < songs.size(); index++) {
            songs.get(index).setSortNo(index);
        }
    }

    /**
     * 收集歌单内歌曲 ID。
     *
     * @return 歌曲 ID 列表
     */
    private List<Long> currentSongIds() {
        List<Long> ids = new ArrayList<>(songs.size());
        for (PlaylistSong item : songs) {
            ids.add(item.getSongId());
        }
        return ids;
    }
}
