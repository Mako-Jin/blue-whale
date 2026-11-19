package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 观看进度上报命令。
 *
 * @author BlueWhale
 */
@Data
public class PlayProgressCommand {

    /** 内容 ID */
    @NotNull(message = "{validation.video.play.content-required}")
    private Long contentId;

    /** 观看进度（毫秒） */
    @NotNull(message = "{validation.video.play.progress-required}")
    private Long progressMs;

    /** 来源：HOME / FEED / SEARCH / SERIES / COLLECTION / MY */
    private String source;

    /** 设备标识：WEB / MOBILE / OTHER */
    private String device;
}
