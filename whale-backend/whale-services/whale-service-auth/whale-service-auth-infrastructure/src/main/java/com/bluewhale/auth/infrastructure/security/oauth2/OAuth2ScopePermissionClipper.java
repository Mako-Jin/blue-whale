package com.bluewhale.auth.infrastructure.security.oauth2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 scope 与用户权限的裁剪器（M1）。
 *
 * <p><b>语义</b>：客户端注册的 scope 是「客户端能力上限」，用户实际拥有的权限码是
 * 「用户能力上限」，两者交集才是本次令牌真正可行使的权限。权限码支持 {@code xxx:*} 通配。</p>
 *
 * <p>本类为纯函数工具，不依赖 Spring，便于单测覆盖边界。</p>
 *
 * @author BlueWhale
 */
public final class OAuth2ScopePermissionClipper {

    /** 通配后缀 */
    private static final String WILDCARD_SUFFIX = ":*";

    /**
     * 私有构造器：工具类禁止实例化
     */
    private OAuth2ScopePermissionClipper() {
    }

    /**
     * 计算「请求 scope ∩ 用户权限码」
     *
     * @param requestedScopes 客户端请求且已注册的 scope
     * @param permissionCodes 用户拥有的权限码，可为 {@code null}
     * @return 裁剪后的 scope 列表，顺序与入参一致
     */
    public static List<String> clip(Collection<String> requestedScopes, Collection<String> permissionCodes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return List.of();
        }
        Set<String> granted = permissionCodes == null ? Set.of() : new HashSet<>(permissionCodes);
        List<String> clipped = new ArrayList<>();
        for (String scope : requestedScopes) {
            if (scope != null && (granted.contains(scope) || matchesWildcard(granted, scope))) {
                clipped.add(scope);
            }
        }
        return List.copyOf(clipped);
    }

    /**
     * 判断 scope 是否命中任意通配权限码
     *
     * @param granted 用户权限码集合
     * @param scope 请求 scope
     * @return {@code true} 表示命中
     */
    private static boolean matchesWildcard(Set<String> granted, String scope) {
        for (String code : granted) {
            if (code.endsWith(WILDCARD_SUFFIX)
                    && scope.startsWith(code.substring(0, code.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}
