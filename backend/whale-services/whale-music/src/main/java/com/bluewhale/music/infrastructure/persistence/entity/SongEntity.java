package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 歌曲实体，对应表 {@code mu_song}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_song")
public class SongEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 可见性：PUBLIC / PRIVATE */
    private String visibility;

    /** 专辑 ID，0 表示单曲 */
    private Long albumId;

    /** 歌手 ID */
    private Long artistId;

    /** 歌曲标题 */
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
    private String publishStatus;

    /** 版权状态 */
    private String copyrightStatus;

    /** 作品上传来源（ORIGINAL / AUTHORIZED / ROYALTY_FREE），对应列 {@code copyright_source} */
    @TableField("copyright_source")
    private String source;

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
}
