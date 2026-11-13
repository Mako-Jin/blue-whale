package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.application.model.vo.GatewayBlacklistVO;
import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.domain.repository.GatewayBlacklistRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("list：透传状态过滤并映射封禁原因")
    void shouldListAllRulesWithStatusFilter() {
        GatewayBlacklistAggregate rule = GatewayBlacklistAggregate.reconstitute(1L, "/pay/**", "*",
                "PLATFORM", "MANUAL", "异常流量", 0, 0, null, null);
        when(gatewayBlacklistRepository.findAll(0)).thenReturn(List.of(rule));

        List<GatewayAccessRuleVO> result = service.list(0);

        assertEquals(1, result.size());
        assertEquals("异常流量", result.get(0).remark());
        assertEquals(0, result.get(0).status());
        verify(gatewayBlacklistRepository).findAll(0);
    }

    @Test
    @DisplayName("create：同路径已封禁时抛业务异常且不落库")
    void shouldRejectDuplicateRuleOnCreate() {
        when(gatewayBlacklistRepository.existsRule(eq("/pay/**"), eq("*"), eq("PLATFORM"), isNull()))
                .thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(new SaveGatewayAccessRuleCommand("/pay/**", null, null, null, null, null, "重复")));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(gatewayBlacklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("update：更新状态与备注后落库")
    void shouldApplyUpdateAndSave() {
        when(gatewayBlacklistRepository.findById(9L))
                .thenReturn(Optional.of(GatewayBlacklistAggregate.reconstitute(9L, "/pay/**", "*",
                        "PLATFORM", 1, 0)));
        when(gatewayBlacklistRepository.existsRule(eq("/pay/**"), eq("POST"), eq("PLATFORM"), eq(9L)))
                .thenReturn(false);

        service.update(9L, new SaveGatewayAccessRuleCommand("/pay/**", "post", null, null, 0, 5, "误封解除"));

        verify(gatewayBlacklistRepository).save(org.mockito.ArgumentMatchers.argThat(rule ->
                "POST".equals(rule.getHttpMethod()) && rule.getStatus() == 0 && "误封解除".equals(rule.getRemark())));
    }

    @Test
    @DisplayName("delete：条目不存在时抛业务异常")
    void shouldRejectDeleteWhenRuleMissing() {
        when(gatewayBlacklistRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.delete(9L));
        verify(gatewayBlacklistRepository, never()).deleteById(9L);
    }
}
