package com.bluewhale.form.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 表单复制请求（编码与名称均可选，为空时由服务端按规则生成）。
 *
 * @author BlueWhale
 */
@Data
public class FormDefinitionCopyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新表单编码（可空/空串；为空时基于源编码生成 {@code <源code>_copy}） */
    @Size(max = 40, message = "表单编码长度不能超过 40")
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{0,39}$",
            message = "表单编码仅允许小写字母、数字、下划线且以字母开头，长度不超过 40")
    private String code;

    /** 新表单名称（可空；为空时生成 {@code <源name>（副本）}） */
    @Size(max = 128, message = "表单名称长度不能超过 128")
    private String name;
}
