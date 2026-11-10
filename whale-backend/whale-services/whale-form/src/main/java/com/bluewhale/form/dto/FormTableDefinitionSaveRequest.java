package com.bluewhale.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 表定义请求（含列集合）。
 *
 * @author BlueWhale
 */
@Data
public class FormTableDefinitionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表编码（必填，最长 64；物理表名来源） */
    @NotBlank(message = "表编码不能为空")
    @Size(max = 64, message = "表编码长度不能超过 64")
    private String tableCode;

    /** 表名称（必填，最长 128） */
    @NotBlank(message = "表名称不能为空")
    @Size(max = 128, message = "表名称长度不能超过 128")
    private String tableName;

    /** 版本号；为空时新增取默认 1 */
    private Integer version;

    /** 备注（最长 255） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    private Integer status;

    /** 列集合（整体替换） */
    private java.util.List<FormColumnMetaDTO> columns;
}
