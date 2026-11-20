package com.bluewhale.scene.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 替换场景可见性规则命令（整表覆盖）。
 *
 * @author BlueWhale
 */
@Data
public class SceneVisibilitySaveCommand {

    /** 规则列表，空列表表示清除全部规则（恢复默认可见） */
    @Valid
    @NotNull(message = "{validation.scene.visibility.rules-required}")
    private List<SceneVisibilityCommand> rules;
}
