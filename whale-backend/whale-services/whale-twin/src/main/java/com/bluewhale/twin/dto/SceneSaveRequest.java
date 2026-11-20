package com.bluewhale.twin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 场景保存请求（创建/更新共用；编码仅创建时生效）。
 *
 * @author BlueWhale
 */
@Data
public class SceneSaveRequest {

    /** 场景编码（租户内唯一，如 plant-demo） */
    @NotBlank(message = "{validation.twin.scene.code-required}")
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "{validation.twin.scene.code-pattern}")
    private String code;

    /** 场景名称 */
    @NotBlank(message = "{validation.twin.scene.name-required}")
    @Size(max = 128, message = "{validation.twin.scene.name-size}")
    private String name;

    /** 场景描述 */
    @Size(max = 512, message = "{validation.twin.scene.description-size}")
    private String description;

    /** 缩略图文件ID（whale-file，可空） */
    private Long thumbnailFileId;

    /** 场景级配置 JSON 字符串（背景/光照/雾效等，可空） */
    @Size(max = 16384, message = "{validation.twin.scene.config-size}")
    private String sceneConfig;
}
