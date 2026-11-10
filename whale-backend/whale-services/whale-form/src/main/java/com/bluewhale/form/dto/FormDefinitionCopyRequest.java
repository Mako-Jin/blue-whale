package com.bluewhale.form.dto;

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

    /** 新表单编码（可空；为空时基于源编码生成 {@code <源code>_copy}） */
    @Size(max = 64, message = "表单编码长度不能超过 64")
    private String code;

    /** 新表单名称（可空；为空时生成 {@code <源name>（副本）}） */
    @Size(max = 128, message = "表单名称长度不能超过 128")
    private String name;
}
