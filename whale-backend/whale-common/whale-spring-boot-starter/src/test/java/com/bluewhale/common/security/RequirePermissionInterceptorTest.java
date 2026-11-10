package com.bluewhale.common.security;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通用权限码头拦截器单测（AUTH-06 / checklist 8.11）。
 *
 * <p>覆盖：无注解直放行、非 HandlerMethod 直放行、{@code super_admin} 放行、命中注解权限码放行、
 * 类级注解生效、码头缺失/为空/不含所需码抛 {@code FORBIDDEN}、码头缺失回落 {@code X-User-Roles}。</p>
 *
 * @author BlueWhale
 */
class RequirePermissionInterceptorTest {

    /** 受保护接口所需权限码 */
    private static final String REQUIRED_CODE = "sys:audit:log:list";

    private final RequirePermissionInterceptor interceptor =
            new RequirePermissionInterceptor(new PermissionHeaderProperties());

    @Test
    @DisplayName("方法无注解：直接放行")
    void shouldPassWhenNoAnnotation() throws Exception {
        assertThat(interceptor.preHandle(request(), response(), handler("openEndpoint"))).isTrue();
    }

    @Test
    @DisplayName("非 HandlerMethod：直接放行")
    void shouldPassWhenHandlerIsNotHandlerMethod() {
        assertThat(interceptor.preHandle(request(), response(), new Object())).isTrue();
    }

    @Test
    @DisplayName("码头含 super_admin：放行")
    void shouldPassWhenContainsSuperAdmin() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(WebHeaders.PERMISSION_CODES, "sys:other:list,super_admin");

        assertThat(interceptor.preHandle(request, response(), handler("protectedEndpoint"))).isTrue();
    }

    @Test
    @DisplayName("码头命中注解权限码：放行")
    void shouldPassWhenContainsRequiredCode() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(WebHeaders.PERMISSION_CODES, "sys:other:list," + REQUIRED_CODE);

        assertThat(interceptor.preHandle(request, response(), handler("protectedEndpoint"))).isTrue();
    }

    @Test
    @DisplayName("类级注解生效：含所需权限码放行")
    void shouldApplyClassLevelAnnotation() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(WebHeaders.PERMISSION_CODES, REQUIRED_CODE);

        assertThat(interceptor.preHandle(request, response(), classLevelHandler())).isTrue();
    }

    @Test
    @DisplayName("码头缺失：抛 FORBIDDEN")
    void shouldThrowForbiddenWhenHeaderMissing() throws Exception {
        assertForbidden(request());
    }

    @Test
    @DisplayName("码头为空串：抛 FORBIDDEN")
    void shouldThrowForbiddenWhenHeaderBlank() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(WebHeaders.PERMISSION_CODES, "   ");

        assertForbidden(request);
    }

    @Test
    @DisplayName("码头不含所需权限码：抛 FORBIDDEN")
    void shouldThrowForbiddenWhenRequiredCodeMissing() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(WebHeaders.PERMISSION_CODES, "sys:other:list");

        assertForbidden(request);
    }

    @Test
    @DisplayName("权限码头缺失：回落 X-User-Roles 头")
    void shouldFallbackToUserRolesHeader() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(WebHeaders.USER_ROLES, REQUIRED_CODE);

        assertThat(interceptor.preHandle(request, response(), handler("protectedEndpoint"))).isTrue();
    }

    /**
     * 断言指定请求被拒绝并抛出 FORBIDDEN 业务异常
     *
     * @param request 模拟请求
     * @throws Exception 反射构造 HandlerMethod 失败
     */
    private void assertForbidden(MockHttpServletRequest request) throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request, response(), handler("protectedEndpoint")))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(ErrorCode.FORBIDDEN.getCode()));
    }

    /**
     * 构造模拟请求
     *
     * @return 模拟请求
     */
    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    /**
     * 构造模拟响应
     *
     * @return 模拟响应
     */
    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    /**
     * 构造方法级注解的 HandlerMethod
     *
     * @param methodName 样例控制器方法名
     * @return HandlerMethod
     * @throws NoSuchMethodException 方法不存在
     */
    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName);
        return new HandlerMethod(new SampleController(), method);
    }

    /**
     * 构造类级注解的 HandlerMethod
     *
     * @return HandlerMethod
     * @throws NoSuchMethodException 方法不存在
     */
    private static HandlerMethod classLevelHandler() throws NoSuchMethodException {
        Method method = ClassLevelController.class.getMethod("anyEndpoint");
        return new HandlerMethod(new ClassLevelController(), method);
    }

    /** 样例控制器（含方法级权限注解与无注解方法） */
    static class SampleController {

        /** 无权限注解方法 */
        public void openEndpoint() {
        }

        /** 受保护方法 */
        @RequirePermission(REQUIRED_CODE)
        public void protectedEndpoint() {
        }
    }

    /** 样例控制器（类级权限注解） */
    @RequirePermission(REQUIRED_CODE)
    static class ClassLevelController {

        /** 继承类级注解的方法 */
        public void anyEndpoint() {
        }
    }
}
