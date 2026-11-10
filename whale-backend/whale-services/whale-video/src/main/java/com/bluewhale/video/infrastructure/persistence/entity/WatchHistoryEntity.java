package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 观看历史实体，对应表 {@code vd_watch_history}。
 *
 * <p>该表无 {@code update_time} / {@code deleted} 列，故不复用 {@code BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("vd_watch_history")
public class WatchHistoryEntity {

    /** 主键（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 内容 ID */
    private Long contentId;

    /** 观看进度（毫秒） */
    private Long progressMs;

    /** 内容时长（毫秒） */
    private Long durationMs;

    /** 来源 */
    private String source;

    /** 设备标识 */
    private String device;

    /** 最近观看时间 */
    private LocalDateTime watchedAt;
}
