package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建或更新文档标签请求。
 *
 * @author BlueWhale
 */
@Data
public class DocTagRequest {

    /** 标签名称 */
    @NotBlank(message = "{validation.doc.tag.name-required}")
    @Size(max = 64, message = "{validation.doc.tag.name-size}")
    private String name;

    /** 标签颜色，形如 #1677ff */
    @Size(max = 16, message = "{validation.doc.tag.color-size}")
    @Pattern(regexp = "^$|#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})", message = "{validation.doc.tag.color-pattern}")
    private String color;
}
