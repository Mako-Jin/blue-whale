package com.bluewhale.auth.application.model.command;

/**
 * 保存用户组命令（权限中心扩展 M3）。
 *
 * <p>{@code id} 为空表示新建，非空表示更新；{@code filtersJson} 仅动态组使用
 * （JSON 数组：{@code [{"field":"deptId","op":"eq","value":10}]}）。</p>
 *
 * @param id 主键（新建时为 {@code null}）
 * @param groupCode 组编码（新建必填，更新不可变）
 * @param groupName 组名称
 * @param parentId 父组 ID（0 表示顶级）
 * @param category 组类别（STATIC / DYNAMIC，新建必填）
 * @param filtersJson 动态组过滤规则 JSON（静态组传 {@code null}）
 * @param description 描述（可空）
 * @param sortNo 排序号（可空）
 * @param status 状态（可空，新建默认启用）
 * @author BlueWhale
 */
public record SaveUserGroupCommand(Long id, String groupCode, String groupName, Long parentId,
                                   String category, String filtersJson, String description,
                                   Integer sortNo, Integer status) {
}
