package com.bluewhale.music.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 歌单加歌命令。
 *
 * @author BlueWhale
 */
@Data
public class AddPlaylistSongCommand {

    /** 歌曲 ID */
    @NotNull(message = "{validation.music.playlist-song.song-id-required}")
    private Long songId;
}
