package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户组保存请求（权限中心扩展 M3）。
 *
 * <p>{@code filtersJson} 仅动态组使用（JSON 数组：{@code [{"field":"deptId","op":"eq","value":10}]}）；
 * 组编码新建必填、更新不可变，故编辑场景可不传。</p>
 *
 * @author BlueWhale
 */
@Data
public class UserGroupSaveRequest {

    /** 组编码（新建必填，更新不可变） */
    @Size(max = 64, message = "{validation.auth.group.code-size}")
    private String groupCode;

    /** 组名称 */
    @NotBlank(message = "{validation.auth.group.name-required}")
    @Size(max = 128, message = "{validation.auth.group.name-size}")
    private String groupName;

    /** 父组 ID（0 表示顶级） */
    @NotNull(message = "{validation.auth.group.parent-id-required}")
    @Min(value = 0, message = "{validation.auth.group.parent-id-min}")
    private Long parentId;

    /** 组类别（STATIC / DYNAMIC，新建必填） */
    @Pattern(regexp = "STATIC|DYNAMIC", message = "{validation.auth.group.type-pattern}")
    private String category;

    /** 动态组过滤规则 JSON（静态组不传） */
    @Size(max = 1024, message = "{validation.auth.group.filter-rule-size}")
    private String filtersJson;

    /** 描述（可空） */
    @Size(max = 255, message = "{validation.auth.common.description-size}")
    private String description;

    /** 排序号（可空） */
    @Min(value = 0, message = "{validation.auth.common.sort-no-negative}")
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用（可空，新建默认启用） */
    @Min(value = 0, message = "{validation.auth.common.status-range}")
    @Max(value = 1, message = "{validation.auth.common.status-range}")
    private Integer status;
}
