package com.bluewhale.music.application.view;

import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.CopyrightSource;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.SongVisibility;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 歌曲视图（运营侧，含文件 ID 用于编辑）。
 *
 * <p>用户侧接口使用 {@link PublicSongView}，不暴露 {@code file_id}（计划 D6）。</p>
 *
 * @author BlueWhale
 */
@Data
public class SongView {

    /** 主键 */
    private Long id;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 可见性 */
    private SongVisibility visibility;

    /** 专辑 ID */
    private Long albumId;

    /** 歌手 ID */
    private Long artistId;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 音频文件 ID */
    private Long audioFileId;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 歌词文件 ID */
    private Long lyricFileId;

    /** 歌词纯文本 */
    private String lyricText;

    /** 语言 */
    private String language;

    /** 发布状态 */
    private PublishState publishState;

    /** 版权状态 */
    private CopyrightState copyrightState;

    /** 作品上传来源（版权合规留痕） */
    private CopyrightSource source;

    /** 播放次数 */
    private Long playCount;

    /** 收藏数 */
    private Long likeCount;

    /** 评论数 */
    private Long commentCount;

    /** 排序权重 */
    private Integer sortWeight;

    /** 最近上架时间 */
    private LocalDateTime publishedAt;

    /** 版权下架原因 */
    private String takedownReason;

    /** 是否可播放 */
    private boolean playable;

    /** 标签 */
    private List<TagView> tags;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
