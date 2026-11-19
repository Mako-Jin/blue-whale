package com.bluewhale.scene.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单条可见性规则命令。
 *
 * @author BlueWhale
 */
@Data
public class SceneVisibilityCommand {

    /** 节点 ID；0 表示场景级规则 */
    private Long nodeId = 0L;

    /** 主体类型：ALL / ROLE / DEPT / USER */
    @NotBlank(message = "{validation.scene.visibility.subject-type-required}")
    private String subjectType;

    /** 主体标识：角色码 / 部门 ID / 用户 ID */
    private String subjectId;

    /** 效果：ALLOW / DENY */
    @NotBlank(message = "{validation.scene.visibility.effect-required}")
    private String effect;
}
