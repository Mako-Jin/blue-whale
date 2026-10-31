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
    @NotNull(message = "歌曲 ID 不能为空")
    private Long songId;

    /** 播放进度（毫秒） */
    @Min(value = 0, message = "播放进度不能为负数")
    private Long progressMs = 0L;

    /** 播放来源：HOME / SEARCH / PLAYLIST / ALBUM / ARTIST / MY */
    @Size(max = 32, message = "播放来源长度不能超过 32")
    private String source;

    /** 设备标识：WEB / MOBILE / OTHER */
    @Size(max = 32, message = "设备标识长度不能超过 32")
    private String device;
}
