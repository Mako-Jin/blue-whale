package com.bluewhale.twin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 孪生实体保存请求（创建/更新共用；编码仅创建时生效）。
 *
 * @author BlueWhale
 */
@Data
public class EntitySaveRequest {

    /** 实体编码（场景内唯一，如 boiler-01） */
    @NotBlank(message = "{validation.twin.entity.code-required}")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]{0,63}$", message = "{validation.twin.entity.code-pattern}")
    private String entityCode;

    /** 实体名称 */
    @NotBlank(message = "{validation.twin.entity.name-required}")
    @Size(max = 128, message = "{validation.twin.entity.name-size}")
    private String name;

    /** 父节点ID（可空表示根节点；更新时用于移动节点） */
    private Long parentId;

    /** 模型资产文件ID（whale-file，GLB/GLTF，可空） */
    private Long modelFileId;

    /** 变换 JSON 字符串（position/rotation/scale，可空） */
    @Size(max = 8192, message = "{validation.twin.entity.transform-size}")
    private String transformJson;

    /** 实体级扩展配置 JSON 字符串（可空） */
    @Size(max = 8192, message = "{validation.twin.entity.extra-config-size}")
    private String extraConfig;

    /** 同级排序号（缺省 0） */
    private Integer sortOrder;
}
