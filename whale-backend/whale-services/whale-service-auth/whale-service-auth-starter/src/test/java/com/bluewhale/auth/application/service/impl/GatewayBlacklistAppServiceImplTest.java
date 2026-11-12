package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;
import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.domain.repository.GatewayBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayBlacklistAppServiceImpl} 单元测试：映射、顺序与解除项过滤。
 *
 * @author BlueWhale
 */
class GatewayBlacklistAppServiceImplTest {

    private final GatewayBlacklistRepository gatewayBlacklistRepository = mock(GatewayBlacklistRepository.class);

    private final GatewayBlacklistAppServiceImpl service =
            new GatewayBlacklistAppServiceImpl(gatewayBlacklistRepository);

    @Test
    @DisplayName("listEnabled：按仓储顺序映射路径与方法")
    void shouldMapEnabledRulesInOrder() {
        when(gatewayBlacklistRepository.findAllEnabled()).thenReturn(List.of(
                GatewayBlacklistAggregate.reconstitute(1L, "/pay/**", "*", "PLATFORM", 1, 1),
                GatewayBlacklistAggregate.reconstitute(2L, "/demo/abuse/**", null, "PLATFORM", 1, 2)));

        List<GatewayBlacklistVO> result = service.listEnabled();

        assertEquals(2, result.size());
        assertEquals("/pay/**", result.get(0).pathPattern());
        assertEquals("*", result.get(0).httpMethod());
        // 方法为空时归一为通配符
        assertEquals("*", result.get(1).httpMethod());
    }

    @Test
    @DisplayName("listEnabled：已解除的封禁项被过滤")
    void shouldFilterDisabledRules() {
        when(gatewayBlacklistRepository.findAllEnabled()).thenReturn(List.of(
                GatewayBlacklistAggregate.reconstitute(1L, "/pay/**", "*", "PLATFORM", 0, 1)));

        assertTrue(service.listEnabled().isEmpty());
    }
}
