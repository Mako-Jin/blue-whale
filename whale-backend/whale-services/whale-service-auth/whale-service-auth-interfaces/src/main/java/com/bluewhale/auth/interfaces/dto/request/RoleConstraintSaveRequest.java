package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色约束保存请求（权限中心扩展 M3）。
 *
 * <p>MUTEX 约束需 ≥2 角色；CARDINALITY 约束 {@code maxAssign} &gt; 0；
 * 约束编码新建必填、租户内唯一、更新不可变。</p>
 *
 * @author BlueWhale
 */
@Data
public class RoleConstraintSaveRequest {

    /** 约束编码（新建必填，更新不可变） */
    @Size(max = 64, message = "{validation.auth.role.constraint-code-size}")
    private String constraintCode;

    /** 约束名称 */
    @NotBlank(message = "{validation.auth.role.constraint-name-required}")
    @Size(max = 128, message = "{validation.auth.role.constraint-name-size}")
    private String constraintName;

    /** 约束类型（MUTEX / CARDINALITY，新建必填） */
    @Pattern(regexp = "MUTEX|CARDINALITY", message = "{validation.auth.role.constraint-type-pattern}")
    private String constraintType;

    /** 约束覆盖的角色 ID 集合（必填非空） */
    @NotEmpty(message = "{validation.auth.role.constraint-role-ids-required}")
    private List<Long> roleIds;

    /** 基数上限（仅 CARDINALITY，可空） */
    @Min(value = 1, message = "{validation.auth.role.constraint-max-assign-min}")
    private Integer maxAssign;

    /** 状态：1 启用 / 0 停用（可空，新建默认启用） */
    @Min(value = 0, message = "{validation.auth.common.status-range}")
    @Max(value = 1, message = "{validation.auth.common.status-range}")
    private Integer status;

    /** 描述（可空） */
    @Size(max = 255, message = "{validation.auth.common.description-size}")
    private String description;
}
