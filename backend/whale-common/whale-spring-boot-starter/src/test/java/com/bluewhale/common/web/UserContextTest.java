package com.bluewhale.common.web;

import com.bluewhale.common.context.RequestContext;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserContext} 单元测试：请求上下文优先、请求头回落与异常口径。
 *
 * @author BlueWhale
 */
class UserContextTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("requireUserId：优先返回请求上下文中的用户 ID")
    void shouldPreferContextUserId() {
        RequestContextHolder.set(new RequestContext(9L, "alice", 3L, "trace-1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12");

        assertEquals(Long.valueOf(9L), UserContext.requireUserId(request));
    }

    @Test
    @DisplayName("requireUserId：无上下文时回落到网关请求头")
    void shouldFallbackToHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12");

        assertEquals(Long.valueOf(12L), UserContext.requireUserId(request));
    }

    @Test
    @DisplayName("requireUserId：缺失用户标识时返回 401")
    void shouldRejectMissingUserId() {
        BizException ex = assertThrows(BizException.class,
                () -> UserContext.requireUserId(new MockHttpServletRequest()));

        assertEquals(401, ex.getCode());
        assertTrue(ex.getMessage().contains("缺少用户标识"));
    }

    @Test
    @DisplayName("requireUserId：用户标识非法时返回 401")
    void shouldRejectInvalidUserId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-a-number");

        BizException ex = assertThrows(BizException.class, () -> UserContext.requireUserId(request));

        assertEquals(401, ex.getCode());
        assertTrue(ex.getMessage().contains("用户标识非法"));
    }

    @Test
    @DisplayName("hasPermission：权限码头命中目标码返回 true")
    void shouldMatchPermissionCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.PERMISSION_CODES, "sys:file:list, sys:file:manage");

        assertTrue(UserContext.hasPermission(request, "sys:file:manage"));
    }

    @Test
    @DisplayName("hasPermission：super_admin 视为拥有全部权限")
    void shouldGrantAllForSuperAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.USER_ROLES, "super_admin");

        assertTrue(UserContext.hasPermission(request, "sys:archive:secret:read"));
    }

    @Test
    @DisplayName("hasPermission：码头缺失、未命中或目标码为空时返回 false")
    void shouldDenyWhenMissingOrNotMatched() {
        assertFalse(UserContext.hasPermission(new MockHttpServletRequest(), "sys:file:manage"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WebHeaders.PERMISSION_CODES, "sys:file:list");
        assertFalse(UserContext.hasPermission(request, "sys:file:manage"));
        assertFalse(UserContext.hasPermission(request, " "));
    }

    @Test
    @DisplayName("findUserId：缺失或非法时返回 null 而不抛异常")
    void shouldReturnNullWhenNoValidUserId() {
        assertNull(UserContext.findUserId(new MockHttpServletRequest()));
        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader("X-User-Id", "bad");
        assertNull(UserContext.findUserId(invalid));
    }
}
