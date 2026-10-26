package com.bluewhale.auth.application.menu.command;

/**
 * 菜单新增/更新命令（AUTH-17 / T-DDD-9）
 *
 * <p>承载菜单写用例的入参，空值默认值由 {@code Menu} 聚合负责，应用层不做兜底。</p>
 *
 * @param parentId 父菜单 ID，为空时按 0（顶级）处理
 * @param name 菜单名称
 * @param path 前端路由路径
 * @param component 前端组件路径
 * @param icon 图标
 * @param sort 排序号，为空时按 0 处理
 * @param menuType 菜单类型（DIR 目录 / MENU 菜单），为空时按 MENU 处理
 * @param permissionCode 关联权限编码
 * @param visible 是否可见（1 显示 / 0 隐藏），为空时按显示处理
 * @param status 状态（1 启用 / 0 停用），为空时按启用处理
 * @author BlueWhale
 */
public record SaveMenuCommand(Long parentId, String name, String path, String component, String icon,
                              Integer sort, String menuType, String permissionCode,
                              Integer visible, Integer status) {
}
