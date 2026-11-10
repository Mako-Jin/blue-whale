package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单歌曲实体，对应表 {@code mu_playlist_song}。
 *
 * <p>该表无 {@code update_time} / {@code deleted} 列，故不复用 {@code BaseEntity}；
 * {@code create_time} 由数据库默认值填充。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("mu_playlist_song")
public class PlaylistSongEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 歌单 ID */
    private Long playlistId;

    /** 歌曲 ID */
    private Long songId;

    /** 歌单内排序 */
    private Integer sortNo;

    /** 加入歌单的用户 ID */
    private Long addedBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
