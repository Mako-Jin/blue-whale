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
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 64, message = "标签名称不能超过 64 字")
    private String name;

    /** 标签颜色，形如 #1677ff */
    @Size(max = 16, message = "标签颜色不能超过 16 字")
    @Pattern(regexp = "^$|#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})", message = "标签颜色格式非法")
    private String color;
}
