package com.bluewhale.mall.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 商城当前操作人（由网关注入的可信身份头解析）。
 *
 * <p>服务自身不解析 JWT、不查 auth 库。缺失 {@code userId} 时按失败关闭：
 * 写操作直接抛 {@link MallErrorCode#ACTOR_REQUIRED}。</p>
 *
 * <p>买家身份只用 {@code userId}：M0 的三种商城形态（零售 / 经销订货 / 员工福利）
 * 下单人都是平台用户，客户与员工档案的映射在 M1 由 CRM / ERP 补齐，
 * 提前在订单里塞一个可能为空且会过期的 {@code customerId} 只会制造两套口径。</p>
 *
 * @author BlueWhale
 */
public final class MallActor {

    /** 可查看全量商城数据的角色 */
    private static final Set<String> MANAGE_ROLES = Set.of("tenant_admin", "mall_admin");

    /** 商城运营（发货、改价、上下架） */
    private static final String MALL_OPERATOR_ROLE = "mall_operator";

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
    public MallActor(Long userId, String username, Set<String> roles, Set<String> permissions) {
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
    public static MallActor anonymous() {
        return new MallActor(null, null, Collections.emptySet(), Collections.emptySet());
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
            throw MallErrorCode.exception(MallErrorCode.ACTOR_REQUIRED);
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
     * 是否具备全量商城数据范围（商城管理员 / 租户管理员）。
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
     * 是否为商城运营（订单发货、商品上下架等履约侧动作）。
     *
     * <p>商城管理员天然包含运营能力；反过来不成立——运营不应能改渠道结算配置。</p>
     *
     * @return 具备运营能力返回 true
     */
    public boolean isOperator() {
        return canManageAll() || roles.contains(MALL_OPERATOR_ROLE);
    }
}
