package com.bluewhale.auth.infrastructure.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
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

    private final Long tenantId;
    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    /** 角色 code 列表 */
    private final List<String> roles;
    private final boolean enabled;
    /** 是否强制首登改密（T-03：出厂默认口令轮换后置位，改密成功后清除） */
    private final boolean mustChangePassword;
    /** 最近一次改密时间（REQ-SEC-023：口令到期判定依据，可空） */
    private final LocalDateTime passwordUpdatedTime;

    /**
     * 构造器：基于用户基础信息与角色 code 列表构建登录用户（不携带强制改密标记，按否处理）
     *
     * @param tenantId 所属租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param password 加密后的密码
     * @param nickname 昵称
     * @param roles 角色 code 列表，内部做不可变拷贝；传 {@code null} 视为空列表
     * @param enabled 账号是否启用
     */
    public LoginUser(Long tenantId, Long userId, String username, String password,
                     String nickname, List<String> roles, boolean enabled) {
        this(tenantId, userId, username, password, nickname, roles, enabled, false, null);
    }

    /**
     * 构造器（全量）：基于用户基础信息与角色 code 列表构建登录用户，含强制首登改密标记
     *
     * @param tenantId 所属租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param password 加密后的密码
     * @param nickname 昵称
     * @param roles 角色 code 列表，内部做不可变拷贝；传 {@code null} 视为空列表
     * @param enabled 账号是否启用
     * @param mustChangePassword 是否强制首登改密
     */
    public LoginUser(Long tenantId, Long userId, String username, String password,
                     String nickname, List<String> roles, boolean enabled, boolean mustChangePassword) {
        this(tenantId, userId, username, password, nickname, roles, enabled, mustChangePassword, null);
    }

    /**
     * 构造器（全量）：额外携带最近一次改密时间（REQ-SEC-023 口令到期判定）
     *
     * @param tenantId 所属租户 ID
     * @param userId 用户 ID
     * @param username 用户名
     * @param password 加密后的密码
     * @param nickname 昵称
     * @param roles 角色 code 列表，内部做不可变拷贝；传 {@code null} 视为空列表
     * @param enabled 账号是否启用
     * @param mustChangePassword 是否强制首登改密
     * @param passwordUpdatedTime 最近一次改密时间（可空）
     */
    public LoginUser(Long tenantId, Long userId, String username, String password,
                     String nickname, List<String> roles, boolean enabled, boolean mustChangePassword,
                     LocalDateTime passwordUpdatedTime) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.passwordUpdatedTime = passwordUpdatedTime;
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
