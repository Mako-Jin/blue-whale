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
    @NotBlank(message = "歌手名称不能为空")
    @Size(max = 100, message = "歌手名称最多 100 个字符")
    private String name;

    /** 头像文件 ID */
    private Long avatarFileId;

    /** 地区 */
    @Size(max = 64, message = "地区最多 64 个字符")
    private String region;

    /** 简介 */
    @Size(max = 1000, message = "简介最多 1000 个字符")
    private String bio;

    /** 状态：1 启用 / 0 停用；缺省启用 */
    private Integer status;
}
