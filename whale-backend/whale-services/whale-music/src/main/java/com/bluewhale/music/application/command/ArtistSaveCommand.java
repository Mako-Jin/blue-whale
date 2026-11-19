package com.bluewhale.music.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 歌手保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ArtistSaveCommand {

    /** 艺名（租户内唯一） */
    @NotBlank(message = "{validation.music.artist.name-required}")
    @Size(max = 100, message = "{validation.music.artist.name-size}")
    private String name;

    /** 头像文件 ID */
    private Long avatarFileId;

    /** 地区 */
    @Size(max = 64, message = "{validation.music.artist.region-size}")
    private String region;

    /** 简介 */
    @Size(max = 1000, message = "{validation.music.artist.bio-size}")
    private String bio;

    /** 状态：1 启用 / 0 停用；缺省启用 */
    private Integer status;
}
