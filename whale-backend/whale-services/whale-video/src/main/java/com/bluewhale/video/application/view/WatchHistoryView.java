package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 观看历史视图（保留已删除内容的标题快照，标记不可播放）。
 *
 * @author BlueWhale
 */
@Data
public class WatchHistoryView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容 ID */
    private Long contentId;

    /** 内容类型 */
    private String contentType;

    /** 标题（内容已删除时为空） */
    private String title;

    /** 封面代理地址 */
    private String coverUrl;

    /** 观看进度（毫秒） */
    private Long progressMs;

    /** 内容时长（毫秒） */
    private Long durationMs;

    /** 来源 */
    private String source;

    /** 最近观看时间 */
    private LocalDateTime watchedAt;

    /** 是否仍可播放 */
    private boolean playable;
}
