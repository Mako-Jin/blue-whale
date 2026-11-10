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
    @NotBlank(message = "状态编码不能为空")
    @Size(max = 32, message = "状态编码长度不能超过 32")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "状态编码需以大写字母开头，仅含大写字母、数字或下划线")
    private String code;

    /** 状态名称 */
    @NotBlank(message = "状态名称不能为空")
    @Size(max = 32, message = "状态名称长度不能超过 32")
    private String name;

    /** 状态分类：TODO / IN_PROGRESS / DONE */
    @NotBlank(message = "状态分类不能为空")
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "状态分类仅支持 TODO / IN_PROGRESS / DONE")
    private String category;

    /** 排序号 */
    private Integer sortNo;

    /** 是否默认状态 */
    private Boolean isDefault;
}
