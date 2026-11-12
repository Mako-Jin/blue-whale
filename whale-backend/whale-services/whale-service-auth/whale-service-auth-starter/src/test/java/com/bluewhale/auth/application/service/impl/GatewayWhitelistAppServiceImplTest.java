package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;
import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;
import com.bluewhale.auth.domain.repository.GatewayWhitelistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayWhitelistAppServiceImpl} 单元测试：映射、顺序与停用项过滤。
 *
 * @author BlueWhale
 */
class GatewayWhitelistAppServiceImplTest {

    private final GatewayWhitelistRepository gatewayWhitelistRepository = mock(GatewayWhitelistRepository.class);

    private final GatewayWhitelistAppServiceImpl service =
            new GatewayWhitelistAppServiceImpl(gatewayWhitelistRepository);

    @Test
    @DisplayName("listEnabled：按仓储顺序映射路径与方法")
    void shouldMapEnabledRulesInOrder() {
        when(gatewayWhitelistRepository.findAllEnabled()).thenReturn(List.of(
                GatewayWhitelistAggregate.reconstitute(1L, "/auth/v1/login", "*", "PLATFORM", 1, 1),
                GatewayWhitelistAggregate.reconstitute(2L, "/music/stream/**", null, "PLATFORM", 1, 2)));

        List<GatewayWhitelistVO> result = service.listEnabled();

        assertEquals(2, result.size());
        assertEquals("/auth/v1/login", result.get(0).pathPattern());
        assertEquals("*", result.get(0).httpMethod());
        // 方法为空时归一为通配符
        assertEquals("*", result.get(1).httpMethod());
    }

    @Test
    @DisplayName("listEnabled：停用项被过滤")
    void shouldFilterDisabledRules() {
        when(gatewayWhitelistRepository.findAllEnabled()).thenReturn(List.of(
                GatewayWhitelistAggregate.reconstitute(1L, "/auth/v1/login", "*", "PLATFORM", 0, 1)));

        assertTrue(service.listEnabled().isEmpty());
    }
}
