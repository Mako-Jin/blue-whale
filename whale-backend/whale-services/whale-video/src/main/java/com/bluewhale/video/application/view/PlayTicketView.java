package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 播放票据视图（单个内容）。
 *
 * <p>票据为随机令牌，绑定当前用户与内容；{@code streamUrl} 不含文件 ID。</p>
 *
 * @author BlueWhale
 */
@Data
public class PlayTicketView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 票据令牌 */
    private String ticket;

    /** 有效期（秒） */
    private int expiresIn;

    /** 建议刷新间隔（秒） */
    private int refreshIn;

    /** 内容 ID */
    private Long contentId;

    /** 内容类型 */
    private String contentType;

    /** 标题 */
    private String title;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 上次观看进度（毫秒，用于断点续播） */
    private Long progressMs;

    /** 流式播放地址 */
    private String streamUrl;
}
