package com.bluewhale.auth.infrastructure.security;

import com.bluewhale.auth.domain.repository.RoleRepository;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionInterceptor} 单元测试（AUTH-23）：授权放行与拒绝路径。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    private static final long USER_ID = 7L;
    private static final String PERM = "sys:user:list";

    @Mock
    private PermissionCache permissionCache;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private PermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor(permissionCache, roleRepository);
    }

    /**
     * 让 mock 的 {@link PermissionCache} 按真实 Cache-Aside 语义调用调用方传入的 loader，
     * 从而间接校验拦截器把 {@code roleRepository::listPermissionCodesByUserId} 作为 loader 传入。
     */
    private void cachePassThrough() {
        when(permissionCache.getPermissionCodes(eq(USER_ID), any())).thenAnswer(invocation -> {
            Supplier<List<String>> loader = invocation.getArgument(1);
            return new LinkedHashSet<>(loader.get());
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** 测试用控制器：承载 @RequirePermission 注解的方法 */
    static class DummyController {
        @RequirePermission(PERM)
        public void protectedApi() {
        }

        @RequirePermission(value = PERM, requireLogin = false)
        public void publicApi() {
        }

        public void openApi() {
        }
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new DummyController(), DummyController.class.getMethod(methodName));
    }

    private static void authenticateAs(List<String> authorities) {
        List<SimpleGrantedAuthority> granted = authorities.stream().map(SimpleGrantedAuthority::new).toList();
        LoginUser principal = new LoginUser(1L, USER_ID, "tester", "pwd", "T", List.of(), true);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("非 HandlerMethod：直接放行")
    void shouldPassThroughNonHandlerMethod() {
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    @DisplayName("无 @RequirePermission：直接放行")
    void shouldPassThroughWithoutAnnotation() throws NoSuchMethodException {
        assertTrue(interceptor.preHandle(request, response, handler("openApi")));
    }

    @Test
    @DisplayName("requireLogin 且无认证：抛 401 UNAUTHORIZED")
    void shouldRejectUnauthenticated() throws NoSuchMethodException {
        BizException ex = assertThrows(BizException.class,
                () -> interceptor.preHandle(request, response, handler("protectedApi")));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("super_admin 角色：直接放行")
    void shouldPassForSuperAdmin() throws NoSuchMethodException {
        authenticateAs(List.of(PermissionInterceptor.SUPER_ADMIN));
        assertTrue(interceptor.preHandle(request, response, handler("protectedApi")));
    }

    @Test
    @DisplayName("authorities 直接含权限码：放行（无需查库）")
    void shouldPassWhenAuthorityMatches() throws NoSuchMethodException {
        authenticateAs(List.of(PERM));
        assertTrue(interceptor.preHandle(request, response, handler("protectedApi")));
    }

    @Test
    @DisplayName("缓存兜底命中权限：放行（loader 委派 roleRepository）")
    void shouldPassViaDbFallback() throws NoSuchMethodException {
        authenticateAs(List.of("common_user"));
        when(roleRepository.listPermissionCodesByUserId(USER_ID)).thenReturn(List.of(PERM));
        cachePassThrough();

        assertTrue(interceptor.preHandle(request, response, handler("protectedApi")));
    }

    @Test
    @DisplayName("缓存兜底未命中：抛 403 FORBIDDEN")
    void shouldRejectWhenNoPermission() throws NoSuchMethodException {
        authenticateAs(List.of("common_user"));
        when(roleRepository.listPermissionCodesByUserId(USER_ID)).thenReturn(List.of("sys:role:list"));
        cachePassThrough();
        BizException ex = assertThrows(BizException.class,
                () -> interceptor.preHandle(request, response, handler("protectedApi")));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("requireLogin=false 且匿名：跳过登录校验，权限码仍校验 → 403")
    void shouldRejectAnonymousPublicApi() throws NoSuchMethodException {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        BizException ex = assertThrows(BizException.class,
                () -> interceptor.preHandle(request, response, handler("publicApi")));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }
}
