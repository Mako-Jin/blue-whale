package com.bluewhale.overview;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.support.Overviews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OverviewController} 单元测试：权限码头判定、身份快照、节点清单。
 *
 * @author BlueWhale
 */
class OverviewControllerTest {

    private OverviewService service;

    private OverviewController controller;

    @BeforeEach
    void setUp() {
        OverviewProperties properties = new OverviewProperties();
        properties.setTtl(Duration.ofSeconds(30));
        properties.setTimeout(Duration.ofMillis(500));
        PerPlaceProvider provider = new PerPlaceProvider();
        service = new OverviewService(List.of(provider), properties, (name, outcome) -> { });
        controller = new OverviewController(service);
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    private static MockHttpServletRequest request(String codesHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        if (codesHeader != null) {
            request.addHeader("X-Permission-Codes", codesHeader);
        }
        return request;
    }

    @Test
    @DisplayName("带权限码的正常请求：返回 live 快照")
    void shouldReturnSnapshotWithPermission() {
        Result<OverviewSnapshot> result = controller.overview(null, request("sys:erp:bi:cockpit:list"));

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().state().getValue()).isEqualTo("live");
        assertThat(result.getData().nodeKey()).isEqualTo("hubu-hall");
    }

    @Test
    @DisplayName("超管放行：不需要显式权限码")
    void shouldAllowSuperAdmin() {
        Result<OverviewSnapshot> result = controller.overview("hubu-hall", request("super_admin"));

        assertThat(result.getCode()).isZero();
    }

    @Test
    @DisplayName("缺权限码：403，而不是返回空 KPI")
    void shouldRejectWithoutPermission() {
        assertThatThrownBy(() -> controller.overview(null, request("sys:other:view")))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("未登录（无任何头）：403，fail-closed")
    void shouldRejectWhenNoHeaders() {
        assertThatThrownBy(() -> controller.overview(null, new MockHttpServletRequest()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("节点清单带出权限码，供运维排查")
    void shouldListNodes() {
        Result<List<OverviewNodeVO>> result = controller.nodes();

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).nodeKey()).isEqualTo("hubu-hall");
        assertThat(result.getData().get(0).permission()).isEqualTo("sys:erp:bi:cockpit:list");
    }

    /**
     * 固定回一个户部快照的 provider，权限码取真实 ERP 口径。
     */
    private static final class PerPlaceProvider implements OverviewProvider {

        @Override
        public String service() {
            return "erp";
        }

        @Override
        public String nodeKey() {
            return "hubu-hall";
        }

        @Override
        public String title() {
            return "户部";
        }

        @Override
        public String permissionCode() {
            return "sys:erp:bi:cockpit:list";
        }

        @Override
        public OverviewSnapshot build(OverviewContext context) {
            return Overviews.builder("erp", "hubu-hall", "户部")
                    .subtitle("经营驾驶舱")
                    .kpi(Overviews.money("revenue", "营收", 1024))
                    .note("权限码集合 " + context.permissionCodes().size())
                    .build();
        }
    }

    /**
     * 仅供编译期校验：超管常量与拦截器同口径（避免两处各写一个字符串）。
     */
    @Test
    @DisplayName("超管常量与拦截器口径一致")
    void superAdminConstantMatchesInterceptor() {
        assertThat(OverviewContext.SUPER_ADMIN)
                .isEqualTo(com.bluewhale.common.security.RequirePermissionInterceptor.SUPER_ADMIN);
        assertThat(Set.of(OverviewContext.SUPER_ADMIN)).contains("super_admin");
    }
}
