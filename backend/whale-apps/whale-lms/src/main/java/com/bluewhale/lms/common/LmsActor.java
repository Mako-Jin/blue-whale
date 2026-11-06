package com.bluewhale.lms.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * LMS 当前操作人（由网关注入的可信身份头解析）。
 *
 * <p>服务自身不解析 JWT、不查询 auth 库；角色与权限集合只用于数据范围判定。
 * 缺失 {@code userId} 时按失败关闭处理：写操作直接抛出
 * {@link LmsErrorCode#ACTOR_REQUIRED}。</p>
 *
 * @author BlueWhale
 */
public final class LmsActor {

    /** 可查看全量课程管理数据的角色 */
    private static final Set<String> MANAGE_ROLES = Set.of("tenant_admin", "lms_admin");

    /** 用户 ID（缺失为 null） */
    private final Long userId;

    /** 用户名 */
    private final String username;

    /** 角色集合 */
    private final Set<String> roles;

    /** 权限码集合 */
    private final Set<String> permissions;

    /**
     * 构造操作人。
     *
     * @param userId 用户 ID，可为 null
     * @param username 用户名，可为 null
     * @param roles 角色集合，可为 null
     * @param permissions 权限码集合，可为 null
     */
    public LmsActor(Long userId, String username, Set<String> roles, Set<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.permissions = permissions == null
                ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    /**
     * 构造匿名操作人：无身份、无角色、无权限。
     *
     * @return 匿名操作人
     */
    public static LmsActor anonymous() {
        return new LmsActor(null, null, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * 查询用户 ID。
     *
     * @return 用户 ID，可能为 null
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 查询用户名。
     *
     * @return 用户名，可能为 null
     */
    public String getUsername() {
        return username;
    }

    /**
     * 查询角色集合。
     *
     * @return 不可变角色集合
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * 获取必填用户 ID。
     *
     * @return 用户 ID
     */
    public Long requireUserId() {
        if (userId == null || userId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.ACTOR_REQUIRED);
        }
        return userId;
    }

    /**
     * 是否拥有任意指定角色。
     *
     * @param codes 角色编码
     * @return 命中任意角色返回 true
     */
    public boolean hasRole(String... codes) {
        if (codes == null) {
            return false;
        }
        for (String code : codes) {
            if (code != null && roles.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为课程管理全量数据范围角色（管理员）。
     *
     * @return 全量数据范围返回 true
     */
    public boolean canManageAll() {
        for (String role : MANAGE_ROLES) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
