package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 空间新增/更新请求。
 *
 * @author BlueWhale
 */
@Data
public class DocSpaceSaveRequest {

    /** 空间编码；创建后不可修改，留空自动生成 */
    @Pattern(regexp = "^[a-zA-Z0-9_-]{2,32}$", message = "{validation.doc.space.space-key-pattern}")
    private String spaceKey;

    /** 空间名称 */
    @NotBlank(message = "{validation.doc.space.name-required}")
    @Size(max = 100, message = "{validation.doc.space.name-size}")
    private String name;

    /** 空间描述 */
    @Size(max = 500, message = "{validation.doc.space.description-size}")
    private String description;

    /** 图标标识 */
    @Size(max = 64, message = "{validation.doc.space.icon-size}")
    private String icon;

    /** 可见性：PUBLIC / MEMBER，默认 MEMBER */
    private String visibility = "MEMBER";
}
