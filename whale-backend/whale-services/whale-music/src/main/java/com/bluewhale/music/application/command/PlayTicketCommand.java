package com.bluewhale.music.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申请播放票据命令。
 *
 * @author BlueWhale
 */
@Data
public class PlayTicketCommand {

    /** 歌曲 ID */
    @NotNull(message = "{validation.music.ticket.song-id-required}")
    private Long songId;
}
