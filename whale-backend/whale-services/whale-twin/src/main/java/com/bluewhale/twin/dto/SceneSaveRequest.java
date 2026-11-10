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
    @NotBlank(message = "场景编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "编码须为小写字母开头的字母/数字/中划线，2-64 位")
    private String code;

    /** 场景名称 */
    @NotBlank(message = "场景名称不能为空")
    @Size(max = 128, message = "名称最长 128 字")
    private String name;

    /** 场景描述 */
    @Size(max = 512, message = "描述最长 512 字")
    private String description;

    /** 缩略图文件ID（whale-file，可空） */
    private Long thumbnailFileId;

    /** 场景级配置 JSON 字符串（背景/光照/雾效等，可空） */
    @Size(max = 16384, message = "场景配置 JSON 最长 16384 字符")
    private String sceneConfig;
}
