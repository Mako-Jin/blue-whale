package com.bluewhale.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 产品保存请求（创建与更新共用；code 创建后不可改）。
 *
 * @author BlueWhale
 */
@Data
public class ProductSaveRequest {

    /** 产品编码（租户内唯一；创建必填，更新忽略） */
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "编码须为小写字母开头的小写字母/数字/连字符，2-64 位")
    private String code;

    /** 产品名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称长度不能超过 128 字")
    private String name;

    /** 产品描述（可空） */
    @Size(max = 512, message = "描述长度不能超过 512 字")
    private String description;
}
