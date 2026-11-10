package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 播放历史实体，对应表 {@code mu_play_history}。
 *
 * <p>该表无 {@code update_time} / {@code deleted} 列，故不复用 {@code BaseEntity}；
 * {@code create_time} 由数据库默认值填充。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("mu_play_history")
public class PlayHistoryEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 歌曲 ID */
    private Long songId;

    /** 播放进度（毫秒） */
    private Long progressMs;

    /** 播放来源 */
    private String source;

    /** 设备标识 */
    private String device;

    /** 最近播放时间 */
    private LocalDateTime playedAt;
}
