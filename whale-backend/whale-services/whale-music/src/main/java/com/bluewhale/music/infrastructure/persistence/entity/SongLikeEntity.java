package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 歌曲收藏实体，对应表 {@code mu_song_like}。
 *
 * @author BlueWhale
 */
@Data
@TableName("mu_song_like")
public class SongLikeEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 歌曲 ID */
    private Long songId;
}
