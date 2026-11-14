package com.bluewhale.common.web.elapsed;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RestApiElapsedAspect} 单元测试：正常/慢调用/异常三条日志分支与调用目标描述。
 *
 * @author BlueWhale
 */
class RestApiElapsedAspectTest {

    private final RestApiElapsedProperties properties = new RestApiElapsedProperties();

    private final RestApiElapsedAspect aspect = new RestApiElapsedAspect(properties);

    @BeforeEach
    void resetContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("正常返回：返回值原样透传，无 RequestContext 时描述为 类名#方法名")
    void shouldPassThroughResultWithoutRequestContext() throws Throwable {
        ProceedingJoinPoint point = point("list", "ok");

        Object result = aspect.around(point);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("慢调用：耗时超过阈值只影响日志级别，返回值不变")
    void shouldLogWarnWhenSlow() throws Throwable {
        properties.setThreshold(0L);
        ProceedingJoinPoint point = point("list", 42);

        assertThat(aspect.around(point)).isEqualTo(42);
    }

    @Test
    @DisplayName("有 Web 请求上下文：描述附加 METHOD /path，返回值不变")
    void shouldDescribeRequestWhenContextPresent() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/order/v1/detail");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint point = point("detail", "payload");

        assertThat(aspect.around(point)).isEqualTo("payload");
    }

    @Test
    @DisplayName("方法抛异常：记录后原样上抛")
    void shouldRethrowOriginalException() throws Throwable {
        ProceedingJoinPoint point = point("create", null);
        when(point.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.around(point))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    /**
     * 构造控制器方法的连接点桩
     *
     * @param methodName 方法名
     * @param result proceed 的返回值
     * @return 连接点
     */
    private static ProceedingJoinPoint point(String methodName, Object result) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn(RestApiElapsedAspectTest.class);
        when(signature.getName()).thenReturn(methodName);
        when(point.getSignature()).thenReturn(signature);
        when(point.proceed()).thenReturn(result);
        return point;
    }
}
