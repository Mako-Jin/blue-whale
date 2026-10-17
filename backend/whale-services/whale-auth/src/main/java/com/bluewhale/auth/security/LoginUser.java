package com.bluewhale.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户（Spring Security UserDetails）
 * <p>authorities 约定：角色 code 原样（如 super_admin），不加 ROLE_ 前缀。</p>
 *
 * @author BlueWhale
 */
@Getter
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    /** 角色 code 列表 */
    private final List<String> roles;
    private final boolean enabled;

    /**
     * 构造器：基于用户基础信息与角色 code 列表构建登录用户
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param password 加密后的密码
     * @param nickname 昵称
     * @param roles 角色 code 列表，内部做不可变拷贝；传 {@code null} 视为空列表
     * @param enabled 账号是否启用
     */
    public LoginUser(Long userId, String username, String password, String nickname, List<String> roles, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
