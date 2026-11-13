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
    @Size(max = 64, message = "组编码长度不能超过 64")
    private String groupCode;

    /** 组名称 */
    @NotBlank(message = "组名称不能为空")
    @Size(max = 128, message = "组名称长度不能超过 128")
    private String groupName;

    /** 父组 ID（0 表示顶级） */
    @NotNull(message = "父组 ID 不能为空")
    @Min(value = 0, message = "父组 ID 不得为负数")
    private Long parentId;

    /** 组类别（STATIC / DYNAMIC，新建必填） */
    @Pattern(regexp = "STATIC|DYNAMIC", message = "组类别仅支持 STATIC / DYNAMIC")
    private String category;

    /** 动态组过滤规则 JSON（静态组不传） */
    @Size(max = 1024, message = "过滤规则 JSON 长度不能超过 1024")
    private String filtersJson;

    /** 描述（可空） */
    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;

    /** 排序号（可空） */
    @Min(value = 0, message = "排序号不得为负数")
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用（可空，新建默认启用） */
    @Min(value = 0, message = "状态仅支持 1（启用）/ 0（停用）")
    @Max(value = 1, message = "状态仅支持 1（启用）/ 0（停用）")
    private Integer status;
}
