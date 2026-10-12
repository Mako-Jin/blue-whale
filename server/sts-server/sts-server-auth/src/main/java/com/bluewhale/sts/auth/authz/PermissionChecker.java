package com.bluewhale.sts.auth.authz;

import com.bluewhale.sts.auth.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限判定入口 —— 全局唯一的"这个人能不能做这件事"的答案来源。
 *
 * <p><b>为什么收口到一处：</b>参照实现把判定拆在了领域聚合的方法里
 * （{@code RoleInfoAggregate.hasPermission}），既没有统一的调用点，
 * 也没有任何接口真的调用它。判定逻辑一旦散落，就会出现"这个接口查角色、
 * 那个接口查权限、第三个接口干脆只查是否登录"的局面 —— 漏洞往往就出在第三种。
 *
 * <p>装载结果缓存在 {@link AuthContext} 上（**请求级**），而不是进程级缓存：
 * 请求级缓存活得短，权限变更最多影响一个进行中的请求；
 * 进程级缓存则要先建一套失效广播，在没有缓存的当前阶段是不必要的复杂度。
 */
@Service
public class PermissionChecker {

    private final AuthorizationMapper authorizationMapper;

    public PermissionChecker(AuthorizationMapper authorizationMapper) {
        this.authorizationMapper = authorizationMapper;
    }

    /** 判定是否拥有某个资源标识的权限 */
    public boolean hasPermission(AuthContext context, String resourceCode) {
        if (context == null || resourceCode == null || resourceCode.isBlank()) {
            return false;
        }
        return permissionsOf(context).contains(resourceCode);
    }

    /** 判定是否**同时**拥有全部资源标识的权限（AND） */
    public boolean hasAllPermissions(AuthContext context, String... resourceCodes) {
        for (String code : resourceCodes) {
            if (!hasPermission(context, code)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取当前用户的权限集合，首次调用时装载并缓存在上下文上。
     *
     * <p>用 {@code contexts.getPermissionsLoaded()} 双检：同一请求里可能有多处判定，
     * 不缓存会让一个请求打出多次相同的四表 JOIN。
     *
     * <p><b>时刻只取一次，两次查询共用。</b>时态授权（{@code effective_from / effective_to}）
     * 让"当前时间"成为判定的一部分：若两次查询各自取一次时间，一个恰好跨过授权到期边界的
     * 请求就可能"角色查得到、权限查不到"。取一次也顺带让缓存的那份结果自洽 ——
     * 放在这里而不是交给 SQL 的 {@code NOW()}，理由见 {@code AuthorizationMapper}。
     */
    @Transactional(readOnly = true)
    public Set<String> permissionsOf(AuthContext context) {
        if (context == null) {
            return Set.of();
        }
        if (context.isPermissionsLoaded()) {
            return context.getPermissions();
        }
        long now = System.currentTimeMillis();
        Set<String> permissions = Set.copyOf(
                authorizationMapper.selectGrantedResourceCodes(context.getTenantId(), context.getUserId(), now));
        // 角色与数据范围由同一条查询取回：两者是同一组有效角色的两个侧面，
        // 分成两条 SQL 就要把"什么算生效"写两遍，而两遍迟早会不一致。
        Set<String> roleCodes = authorizationMapper.selectEffectiveRoles(
                        context.getTenantId(), context.getUserId(), now)
                .stream()
                .map(RoleScopeView::getRoleCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());

        context.setPermissions(permissions);
        context.setRoleCodes(roleCodes);
        context.setPermissionsLoaded(true);
        return permissions;
    }

}
