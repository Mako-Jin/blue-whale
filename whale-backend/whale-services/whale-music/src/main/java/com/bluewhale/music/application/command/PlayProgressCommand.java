package com.bluewhale.music.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上报播放进度命令（同一用户同一歌曲重复上报幂等）。
 *
 * @author BlueWhale
 */
@Data
public class PlayProgressCommand {

    /** 歌曲 ID */
    @NotNull(message = "{validation.music.progress.song-id-required}")
    private Long songId;

    /** 播放进度（毫秒） */
    @Min(value = 0, message = "{validation.music.progress.progress-min}")
    private Long progressMs = 0L;

    /** 播放来源：HOME / SEARCH / PLAYLIST / ALBUM / ARTIST / MY */
    @Size(max = 32, message = "{validation.music.progress.source-size}")
    private String source;

    /** 设备标识：WEB / MOBILE / OTHER */
    @Size(max = 32, message = "{validation.music.progress.device-size}")
    private String device;
}
