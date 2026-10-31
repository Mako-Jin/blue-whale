package com.bluewhale.music.application.view;

import com.bluewhale.music.domain.model.SongVisibility;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户侧歌曲视图（不暴露 {@code file_id}）。
 *
 * @author BlueWhale
 */
@Data
public class PublicSongView {

    /** 主键 */
    private Long id;

    /** 可见性 */
    private SongVisibility visibility;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 专辑 ID */
    private Long albumId;

    /** 歌手 ID */
    private Long artistId;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 语言 */
    private String language;

    /** 专辑封面文件 ID（公开详情允许展示封面，播放地址由 M2 票据代理提供） */
    private Long coverFileId;

    /** 播放次数 */
    private Long playCount;

    /** 收藏数 */
    private Long likeCount;

    /** 评论数 */
    private Long commentCount;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 是否可播放 */
    private boolean playable;

    /** 标签 */
    private List<TagView> tags;
}
