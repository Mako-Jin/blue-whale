package com.bluewhale.auth.infrastructure.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OAuth2ScopePermissionClipper} 单元测试（M1）。
 *
 * <p>校验「请求 scope ∩ 用户权限码」的裁剪口径，含通配权限码与空入参边界。</p>
 *
 * @author BlueWhale
 */
class OAuth2ScopePermissionClipperTest {

    /**
     * 仅保留用户实际拥有的 scope，顺序与入参一致
     */
    @Test
    @DisplayName("裁剪：只保留交集且保持入参顺序")
    void shouldKeepIntersectionInOrder() {
        List<String> clipped = OAuth2ScopePermissionClipper.clip(
                List.of("sys:user:list", "sys:role:list", "openid"),
                Set.of("sys:role:list", "sys:user:list"));

        assertEquals(List.of("sys:user:list", "sys:role:list"), clipped);
    }

    /**
     * 通配权限码命中同前缀 scope
     */
    @Test
    @DisplayName("通配权限码 sys:user:* 命中 sys:user:list")
    void shouldMatchWildcardPermission() {
        List<String> clipped = OAuth2ScopePermissionClipper.clip(
                List.of("sys:user:list", "sys:role:list"),
                Set.of("sys:user:*"));

        assertEquals(List.of("sys:user:list"), clipped);
    }

    /**
     * 空入参返回空结果，不抛异常
     */
    @Test
    @DisplayName("空 scope 或空权限码返回空列表")
    void shouldReturnEmptyOnBlankInput() {
        assertTrue(OAuth2ScopePermissionClipper.clip(null, Set.of("a")).isEmpty());
        assertTrue(OAuth2ScopePermissionClipper.clip(List.of(), Set.of("a")).isEmpty());
        assertTrue(OAuth2ScopePermissionClipper.clip(List.of("a"), null).isEmpty());
    }
}
