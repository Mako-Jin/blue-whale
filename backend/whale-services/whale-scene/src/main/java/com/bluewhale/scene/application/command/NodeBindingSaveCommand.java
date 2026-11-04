package com.bluewhale.scene.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 节点入口绑定命令。
 *
 * @author BlueWhale
 */
@Data
public class NodeBindingSaveCommand {

    /** 入口类型：SYSTEM / PAGE / KNOWLEDGE / EXTERNAL */
    @NotBlank(message = "入口类型不能为空")
    private String entryType;

    /** 目标地址：平台内路径或 http/https 外链 */
    @NotBlank(message = "入口地址不能为空")
    @Size(max = 512, message = "入口地址不能超过 512 个字符")
    private String targetPath;

    /** 访问所需权限码，可为空 */
    @Size(max = 128, message = "权限码不能超过 128 个字符")
    private String permissionCode;

    /** 是否启用，默认启用 */
    private Boolean enabled = Boolean.TRUE;
}
