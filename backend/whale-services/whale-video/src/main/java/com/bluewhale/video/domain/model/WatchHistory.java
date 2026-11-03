package com.bluewhale.video.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 观看历史 / 进度（每用户每内容仅保留一条，重复上报更新进度与最近观看时间）。
 *
 * <p>只有当前可见且可播放的内容才会写入；按用户保留 180 天（可配置），
 * 超期由清理任务删除。</p>
 *
 * @author BlueWhale
 */
@Data
public class WatchHistory {

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 内容 ID */
    private Long contentId;

    /** 观看进度（毫秒） */
    private Long progressMs;

    /** 内容时长（毫秒，用于续播比例） */
    private Long durationMs;

    /** 来源：HOME / FEED / SEARCH / SERIES / COLLECTION / MY */
    private String source;

    /** 设备标识：WEB / MOBILE / OTHER */
    private String device;

    /** 最近观看时间 */
    private LocalDateTime watchedAt;
}
