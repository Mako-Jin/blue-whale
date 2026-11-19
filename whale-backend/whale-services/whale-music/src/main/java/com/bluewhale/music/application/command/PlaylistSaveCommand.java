package com.bluewhale.music.application.command;

import com.bluewhale.music.domain.model.PlaylistVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 歌单创建 / 修改命令（用户歌单与官方歌单共用）。
 *
 * @author BlueWhale
 */
@Data
public class PlaylistSaveCommand {

    /** 歌单标题 */
    @NotBlank(message = "{validation.music.playlist.title-required}")
    @Size(max = 120, message = "{validation.music.playlist.title-size}")
    private String title;

    /** 歌单简介 */
    @Size(max = 1000, message = "{validation.music.playlist.description-size}")
    private String description;

    /** 可见性：PUBLIC 公开 / PRIVATE 仅所有者；缺省 PUBLIC */
    private PlaylistVisibility visibility;

    /** 封面文件 ID */
    private Long coverFileId;
}
