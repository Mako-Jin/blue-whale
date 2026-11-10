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
    @Pattern(regexp = "^[a-zA-Z0-9_-]{2,32}$", message = "空间编码仅允许 2-32 位字母、数字、下划线或中划线")
    private String spaceKey;

    /** 空间名称 */
    @NotBlank(message = "空间名称不能为空")
    @Size(max = 100, message = "空间名称不能超过 100 字")
    private String name;

    /** 空间描述 */
    @Size(max = 500, message = "空间描述不能超过 500 字")
    private String description;

    /** 图标标识 */
    @Size(max = 64, message = "图标标识不能超过 64 字")
    private String icon;

    /** 可见性：PUBLIC / MEMBER，默认 MEMBER */
    private String visibility = "MEMBER";
}
