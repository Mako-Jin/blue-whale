package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作项状态新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class StatusSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态编码（项目内唯一，大写字母开头） */
    @NotBlank(message = "{validation.pm.status.code-required}")
    @Size(max = 32, message = "{validation.pm.status.code-size}")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "{validation.pm.status.code-pattern}")
    private String code;

    /** 状态名称 */
    @NotBlank(message = "{validation.pm.status.name-required}")
    @Size(max = 32, message = "{validation.pm.status.name-size}")
    private String name;

    /** 状态分类：TODO / IN_PROGRESS / DONE */
    @NotBlank(message = "{validation.pm.status.category-required}")
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "{validation.pm.status.category-pattern}")
    private String category;

    /** 排序号 */
    private Integer sortNo;

    /** 是否默认状态 */
    private Boolean isDefault;
}
