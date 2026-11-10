package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户响应：供前端动态路由与按钮级权限渲染使用
 *
 * @author BlueWhale
 */
@Data
public class CurrentUserResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long userId;
    private String username;
    private String nickname;
    /** 角色 code 列表 */
    private List<String> roles;
    /** 权限码列表（按钮级权限判定的依据） */
    private List<String> permissions;
    /** 当前用户可见菜单树 */
    private List<MenuResponse> menus;
}
