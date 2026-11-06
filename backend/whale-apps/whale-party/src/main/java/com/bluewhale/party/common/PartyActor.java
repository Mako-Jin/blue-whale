package com.bluewhale.party.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 党政工会当前操作人（由网关注入的可信身份头解析）。
 *
 * <p>服务自身不解析 JWT、不查询 auth 库；角色与权限集合只用于数据范围判定。
 * 缺失 {@code userId} 时按失败关闭处理：写操作直接抛出
 * {@link PartyErrorCode#ACTOR_REQUIRED}。</p>
 *
 * <p>支部书记的「本支部范围」不走这里判断：本服务不存「用户 → 支部」映射，
 * 支部范围的判定落在党员档案上（书记账号在 {@code pty_member.user_id} 上关联到某个支部），
 * 由应用服务按需查询，避免在操作人对象里塞进一个会过期的组织快照。</p>
 *
 * @author BlueWhale
 */
public final class PartyActor {

    /** 可查看全量党务数据的角色（党务管理员与租户管理员） */
    private static final Set<String> MANAGE_ROLES = Set.of("tenant_admin", "party_admin");

    /** 工会管理员（M-P3 起用于工会数据范围） */
    private static final String UNION_ADMIN_ROLE = "union_admin";

    /** 支部书记（本支部数据范围） */
    private static final String BRANCH_SECRETARY_ROLE = "branch_secretary";

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
    public PartyActor(Long userId, String username, Set<String> roles, Set<String> permissions) {
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
    public static PartyActor anonymous() {
        return new PartyActor(null, null, Collections.emptySet(), Collections.emptySet());
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
     * 查询权限码集合。
     *
     * @return 不可变权限集合
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * 获取必填用户 ID。
     *
     * @return 用户 ID
     */
    public Long requireUserId() {
        if (userId == null || userId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTOR_REQUIRED);
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
     * 是否具备全量党务数据范围（党务管理员 / 租户管理员）。
     *
     * @return 全量范围返回 true
     */
    public boolean canManageAll() {
        for (String role : MANAGE_ROLES) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为支部书记（只看本支部数据）。
     *
     * @return 支部书记返回 true
     */
    public boolean isBranchSecretary() {
        return roles.contains(BRANCH_SECRETARY_ROLE);
    }

    /**
     * 是否为工会管理员。
     *
     * @return 工会管理员返回 true
     */
    public boolean isUnionAdmin() {
        return roles.contains(UNION_ADMIN_ROLE);
    }
}
