package com.bluewhale.form.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 表定义请求（含列集合）。
 *
 * @author BlueWhale
 */
@Data
public class FormTableDefinitionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表编码（必填，小写字母开头，仅字母/数字/下划线，最长 40；物理表名来源） */
    @NotBlank(message = "表编码不能为空")
    @Size(max = 40, message = "表编码长度不能超过 40")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$",
            message = "表编码仅允许小写字母、数字、下划线且以字母开头，长度不超过 40")
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
    @Valid
    private List<FormColumnMetaDTO> columns;
}
