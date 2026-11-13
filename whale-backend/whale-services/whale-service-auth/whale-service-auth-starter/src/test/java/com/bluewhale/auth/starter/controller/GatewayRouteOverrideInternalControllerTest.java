package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.vo.GatewayRouteOverrideVO;
import com.bluewhale.auth.application.service.GatewayRouteOverrideAppService;
import com.bluewhale.auth.interfaces.dto.response.GatewayRouteOverrideItemResponse;
import com.bluewhale.auth.starter.assemble.GatewayRouteOverrideAssemblerImpl;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayRouteOverrideInternalController} 单元测试：下发项字段映射与空列表语义。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class GatewayRouteOverrideInternalControllerTest {

    @Mock
    private GatewayRouteOverrideAppService gatewayRouteOverrideAppService;

    private GatewayRouteOverrideInternalController controller;

    @BeforeEach
    void setUp() {
        controller = new GatewayRouteOverrideInternalController(gatewayRouteOverrideAppService,
                new GatewayRouteOverrideAssemblerImpl());
    }

    @Test
    @DisplayName("listAll：下发项仅含网关匹配所需字段，停用条目同样下发")
    void shouldReturnAllItemsWithGatewayFields() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 13, 8, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 13, 22, 0);
        when(gatewayRouteOverrideAppService.listAll()).thenReturn(List.of(
                new GatewayRouteOverrideVO("whale-demo", false, from, to, 3L),
                new GatewayRouteOverrideVO("whale-audit", true, null, null, 1L)));

        Result<List<GatewayRouteOverrideItemResponse>> result = controller.listAll();

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        GatewayRouteOverrideItemResponse disabled = result.getData().get(0);
        assertEquals("whale-demo", disabled.routeId());
        assertFalse(disabled.enabled());
        assertEquals(from, disabled.effectiveFrom());
        assertEquals(to, disabled.effectiveTo());
        assertEquals(3L, disabled.version());
        GatewayRouteOverrideItemResponse enabled = result.getData().get(1);
        assertEquals("whale-audit", enabled.routeId());
        assertTrue(enabled.enabled());
        assertEquals(1L, enabled.version());
    }

    @Test
    @DisplayName("listAll：无覆盖条目时返回空列表")
    void shouldReturnEmptyListWhenNoOverrides() {
        when(gatewayRouteOverrideAppService.listAll()).thenReturn(List.of());

        Result<List<GatewayRouteOverrideItemResponse>> result = controller.listAll();

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
    }
}
