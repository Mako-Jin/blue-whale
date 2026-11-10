package com.bluewhale.scene.application.support;

import java.util.Set;

/**
 * 导航请求的访问者身份（由接口层从网关注入的身份头解析）。
 *
 * @param userId 用户 ID
 * @param roles 角色码集合
 * @param permissions 权限码集合
 * @param deptId 部门 ID（首期为空，部门粒度后置）
 * @author BlueWhale
 */
public record NavigationUser(Long userId, Set<String> roles, Set<String> permissions, String deptId) {

    /**
     * 构造仅含权限的空身份，便于测试与登录态兜底。
     *
     * @param permissions 权限码集合
     * @return 访问者身份
     */
    public static NavigationUser ofPermissions(Set<String> permissions) {
        return new NavigationUser(null, Set.of(), permissions, null);
    }
}
