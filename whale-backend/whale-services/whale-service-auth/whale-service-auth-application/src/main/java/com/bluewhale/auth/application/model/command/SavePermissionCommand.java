package com.bluewhale.auth.application.model.command;

/**
 * 权限点新增/更新命令（AUTH-17 / T-DDD-9）
 *
 * <p>承载权限点写用例的入参，空值默认值由 {@code Permission} 聚合负责，应用层不做兜底。</p>
 *
 * @param parentId 父权限点 ID，为空时按 0（顶级）处理
 * @param permCode 权限编码（须满足 {@code PermCode} 格式）
 * @param permName 权限名称
 * @param permType 权限类型（SYSTEM / MENU / API / BUTTON / DATA / ATTR）
 * @param path 接口路径
 * @param method 请求方法
 * @param sortNo 排序号，为空时按 0 处理
 * @param status 状态（1 启用 / 0 停用），为空时按启用处理
 * @author BlueWhale
 */
public record SavePermissionCommand(Long parentId, String permCode, String permName, String permType,
                                    String path, String method, Integer sortNo, Integer status) {
}
