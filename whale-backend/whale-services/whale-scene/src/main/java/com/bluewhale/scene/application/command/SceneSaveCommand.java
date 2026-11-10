package com.bluewhale.scene.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 场景新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class SceneSaveCommand {

    /** 场景标识（编辑已发布场景时不可修改） */
    @NotBlank(message = "场景标识不能为空")
    @Size(max = 64, message = "场景标识不能超过 64 个字符")
    private String sceneKey;

    /** 场景名称 */
    @NotBlank(message = "场景名称不能为空")
    @Size(max = 128, message = "场景名称不能超过 128 个字符")
    private String name;

    /** 场景说明 */
    @Size(max = 500, message = "场景说明不能超过 500 个字符")
    private String description;
}
