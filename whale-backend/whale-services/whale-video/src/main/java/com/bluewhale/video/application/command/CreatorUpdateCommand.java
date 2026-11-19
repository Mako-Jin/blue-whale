package com.bluewhale.video.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创作者运营编辑命令（昵称 / 简介 / 状态 / 头像）。
 *
 * @author BlueWhale
 */
@Data
public class CreatorUpdateCommand {

    /** 创作者昵称 */
    @Size(max = 64, message = "{validation.video.creator.display-name-size}")
    private String displayName;

    /** 简介 */
    @Size(max = 500, message = "{validation.video.creator.bio-size}")
    private String bio;

    /** 状态：1 启用 / 0 停用，为空表示保持不变 */
    private Integer status;

    /** 头像文件 ID；为空表示保留原头像，0 表示清除 */
    private Long avatarFileId;
}
