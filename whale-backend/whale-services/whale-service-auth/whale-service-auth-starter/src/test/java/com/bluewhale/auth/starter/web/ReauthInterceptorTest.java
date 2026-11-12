package com.bluewhale.auth.starter.web;

import com.bluewhale.auth.application.service.ReauthAppService;
import com.bluewhale.auth.application.security.ReauthRequiredException;
import com.bluewhale.auth.sdk.annotation.RequireReauth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReauthInterceptor} 单元测试（Wave 6.22）
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("二次认证拦截器")
class ReauthInterceptorTest {

    @Mock
    private ReauthAppService reauthAppService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    /** 样例控制器：一个标注解、一个不标 */
    static class SampleController {

        /** 受保护端点 */
        @RequireReauth
        public void guarded() {
        }

        /** 普通端点 */
        public void open() {
        }
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getMethod(methodName));
    }

    private ReauthInterceptor newInterceptor(List<String> rules) {
        return new ReauthInterceptor(reauthAppService, rules);
    }

    @Test
    @DisplayName("未标注注解且不命中规则：直接放行，不查凭证")
    void openEndpointPasses() throws Exception {
        assertTrue(newInterceptor(List.of()).preHandle(request, response, handler("open")));
        verify(reauthAppService, never()).verify(any());
    }

    @Test
    @DisplayName("标注注解且凭证有效：放行")
    void guardedWithValidTokenPasses() throws Exception {
        when(request.getHeader(ReauthInterceptor.HEADER_NAME)).thenReturn("token-1");
        when(reauthAppService.verify("token-1")).thenReturn(true);
        assertTrue(newInterceptor(List.of()).preHandle(request, response, handler("guarded")));
    }

    @Test
    @DisplayName("标注注解且凭证无效：抛 ReauthRequiredException（映射 HTTP 428 / 42801）")
    void guardedWithoutTokenThrowsReauthRequired() throws Exception {
        when(request.getHeader(ReauthInterceptor.HEADER_NAME)).thenReturn(null);
        when(reauthAppService.verify(null)).thenReturn(false);
        ReauthRequiredException e = assertThrows(ReauthRequiredException.class,
                () -> newInterceptor(List.of()).preHandle(request, response, handler("guarded")));
        assertEquals(42801, ReauthRequiredException.CODE);
        assertEquals("REAUTH_REQUIRED", e.getMessage());
    }

    @Test
    @DisplayName("路径规则兜底：命中清单且未带凭证 → 428（注解无关）")
    void pathRuleProtectsUnannotatedEndpoint() throws Exception {
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/user/1/status");
        when(request.getHeader(ReauthInterceptor.HEADER_NAME)).thenReturn(null);
        when(reauthAppService.verify(null)).thenReturn(false);
        assertThrows(ReauthRequiredException.class, () -> newInterceptor(List.of("PUT /user/*/status"))
                .preHandle(request, response, handler("open")));
    }

    @Test
    @DisplayName("路径规则按方法区分：GET 同路径不受约束")
    void pathRuleMatchesMethod() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/user/1/status");
        assertTrue(newInterceptor(List.of("PUT /user/*/status"))
                .preHandle(request, response, handler("open")));
    }

    @Test
    @DisplayName("自助改密不进默认清单：不带凭证也不拦（否则与待改密状态互锁）")
    void selfPasswordChangeNotProtectedByDefault() throws Exception {
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/auth/v1/password");
        assertTrue(newInterceptor(List.of()).preHandle(request, response, handler("open")));
    }

    /** API-first 形态：注解标在接口方法上，实现类不重复标注 */
    interface ApiFirst {
        /** 改密端点（接口声明二次认证） */
        @RequireReauth
        void changePassword();
    }

    /** 实现类：方法本身无注解 */
    static class ApiFirstImpl implements ApiFirst {
        @Override
        public void changePassword() {
        }
    }

    @Test
    @DisplayName("注解标在 API 接口方法上（AuthApi 形态）：实现类同样受约束")
    void interfaceAnnotationIsResolved() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new ApiFirstImpl(), ApiFirstImpl.class.getMethod("changePassword"));
        when(request.getHeader(ReauthInterceptor.HEADER_NAME)).thenReturn(null);
        when(reauthAppService.verify(null)).thenReturn(false);
        assertThrows(ReauthRequiredException.class,
                () -> newInterceptor(List.of()).preHandle(request, response, handlerMethod));
    }
}
