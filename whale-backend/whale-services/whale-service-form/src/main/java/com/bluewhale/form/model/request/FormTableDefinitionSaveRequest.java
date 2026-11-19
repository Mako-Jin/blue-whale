package com.bluewhale.form.model.request;

import com.bluewhale.form.model.dto.FormColumnMetaDTO;
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
    @NotBlank(message = "{validation.form.table-definition.code-required}")
    @Size(max = 40, message = "{validation.form.table-definition.code-size}")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$",
            message = "{validation.form.table-definition.code-pattern}")
    private String tableCode;

    /** 表名称（必填，最长 128） */
    @NotBlank(message = "{validation.form.table-definition.name-required}")
    @Size(max = 128, message = "{validation.form.table-definition.name-size}")
    private String tableName;

    /** 版本号；为空时新增取默认 1 */
    private Integer version;

    /** 备注（最长 255） */
    @Size(max = 255, message = "{validation.form.table-definition.remark-size}")
    private String remark;

    /** 状态：1 正常 / 0 禁用；为空时新增取默认 1 */
    private Integer status;

    /** 列集合（整体替换） */
    @Valid
    private List<FormColumnMetaDTO> columns;
}
