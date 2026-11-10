package com.bluewhale.auth.application.model.result;

import com.bluewhale.auth.application.vo.MenuVO;

import java.util.List;

/**
 * 当前登录用户结果：角色 / 权限码 / 可见菜单树。
 *
 * @param tenantId 租户 ID
 * @param userId 用户 ID
 * @param username 用户名
 * @param nickname 昵称
 * @param roles 角色 code 列表
 * @param permissions 权限码列表
 * @param menus 可见菜单树
 * @author BlueWhale
 */
public record CurrentUserResult(Long tenantId,
                                Long userId,
                                String username,
                                String nickname,
                                List<String> roles,
                                List<String> permissions,
                                List<MenuVO> menus) {
}
