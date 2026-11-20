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
    @NotBlank(message = "{validation.scene.scene.scene-key-required}")
    @Size(max = 64, message = "{validation.scene.scene.scene-key-size}")
    private String sceneKey;

    /** 场景名称 */
    @NotBlank(message = "{validation.scene.scene.name-required}")
    @Size(max = 128, message = "{validation.scene.scene.name-size}")
    private String name;

    /** 场景说明 */
    @Size(max = 500, message = "{validation.scene.scene.description-size}")
    private String description;
}
