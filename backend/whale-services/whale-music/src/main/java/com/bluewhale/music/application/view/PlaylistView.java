package com.bluewhale.music.application.view;

import com.bluewhale.music.domain.model.PlaylistType;
import com.bluewhale.music.domain.model.PlaylistVisibility;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 歌单视图。
 *
 * <p>歌单歌曲详情在 {@link #songs} 中返回用户侧歌曲字段（不含音频文件 ID），
 * 分页列表场景不填充该字段。</p>
 *
 * @author BlueWhale
 */
@Data
public class PlaylistView {

    /** 主键 */
    private Long id;

    /** 所有者用户 ID，官方歌单为 0 */
    private Long ownerId;

    /** 标题 */
    private String title;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 简介 */
    private String description;

    /** 歌单类型 */
    private PlaylistType playlistType;

    /** 可见性 */
    private PlaylistVisibility visibility;

    /** 歌曲数量 */
    private Integer songCount;

    /** 播放次数 */
    private Long playCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 歌单内歌曲（详情时填充，含排序号） */
    private List<PlaylistSongView> songs;
}
