package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.vo.RoleVO;

/**
 * 角色继承应用服务（权限中心扩展 M3）：父角色挂载 / 解除与继承策略、分类变更。
 *
 * @author BlueWhale
 */
public interface RoleHierarchyAppService {

    /**
     * 挂载到父角色（层级只能父 +1；沿父链校验不得成环）
     *
     * @param roleId 角色 ID
     * @param parentRoleId 父角色 ID
     * @return 更新后的角色视图
     */
    RoleVO attachParent(Long roleId, Long parentRoleId);

    /**
     * 解除父角色（回到顶级）
     *
     * @param roleId 角色 ID
     * @return 更新后的角色视图
     */
    RoleVO detachParent(Long roleId);

    /**
     * 变更继承策略
     *
     * @param roleId 角色 ID
     * @param strategy 继承策略字符串（FULL / INCREMENT / OVERRIDE / NONE）
     * @return 更新后的角色视图
     */
    RoleVO changeInheritStrategy(Long roleId, String strategy);

    /**
     * 变更角色分类
     *
     * @param roleId 角色 ID
     * @param category 分类（GENERAL / BUSINESS / SYSTEM）
     * @return 更新后的角色视图
     */
    RoleVO changeCategory(Long roleId, String category);
}
