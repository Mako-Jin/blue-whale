package com.bluewhale.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TenantContextFilter} 单元测试（Wave 5.54 Phase 2）：租户头解析、非法头忽略、请求结束必清理。
 *
 * @author BlueWhale
 */
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("合法 X-Tenant-Id：链路内可读到租户上下文，链路结束后清理")
    void shouldBindTenantDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/order/v1/list");
        request.addHeader("X-Tenant-Id", " 1001 ");
        AtomicReference<Long> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(insideChain));

        assertThat(insideChain.get()).isEqualTo(1001L);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("非法 X-Tenant-Id：忽略该头，链路照常执行且上下文为空")
    void shouldIgnoreIllegalTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/order/v1/list");
        request.addHeader("X-Tenant-Id", "not-a-number");
        AtomicReference<Long> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(insideChain));

        assertThat(insideChain.get()).isNull();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("无 X-Tenant-Id（登录/刷新白名单链路）：不绑定上下文")
    void shouldKeepEmptyWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/v1/login");
        AtomicReference<Long> insideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(insideChain));

        assertThat(insideChain.get()).isNull();
    }

    @Test
    @DisplayName("链路抛异常时仍清理上下文，异常原样上抛")
    void shouldClearTenantEvenWhenChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/order/v1/list");
        request.addHeader("X-Tenant-Id", "2002");
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("downstream boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream boom");
        assertThat(TenantContext.get()).isNull();
    }

    /**
     * 构造一条在链路内记录当前线程租户 ID 的过滤器链
     *
     * @param holder 记录租户 ID 的容器
     * @return 过滤器链
     */
    private static FilterChain capture(AtomicReference<Long> holder) {
        return (req, res) -> holder.set(TenantContext.get());
    }
}
