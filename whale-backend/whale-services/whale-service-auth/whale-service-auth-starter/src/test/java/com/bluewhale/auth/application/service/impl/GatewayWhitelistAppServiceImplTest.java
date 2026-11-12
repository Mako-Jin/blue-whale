package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveGatewayAccessRuleCommand;
import com.bluewhale.auth.application.model.vo.GatewayAccessRuleVO;
import com.bluewhale.auth.application.model.vo.GatewayWhitelistVO;
import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;
import com.bluewhale.auth.domain.repository.GatewayWhitelistRepository;
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

    @Test
    @DisplayName("list：透传状态过滤并映射管理字段")
    void shouldListAllRulesWithStatusFilter() {
        GatewayWhitelistAggregate rule = GatewayWhitelistAggregate.reconstitute(1L, "/auth/v1/login", null,
                "PLATFORM", "MIGRATION", "登录放行", 0, 1, null, null);
        when(gatewayWhitelistRepository.findAll(0)).thenReturn(List.of(rule));

        List<GatewayAccessRuleVO> result = service.list(0);

        assertEquals(1, result.size());
        assertEquals("MIGRATION", result.get(0).source());
        assertEquals(0, result.get(0).status());
        assertEquals("登录放行", result.get(0).remark());
        verify(gatewayWhitelistRepository).findAll(0);
    }

    @Test
    @DisplayName("create：方法归一为大写并落库")
    void shouldNormalizeMethodOnCreate() {
        when(gatewayWhitelistRepository.existsRule(eq("/ops/**"), eq("GET"), eq("PLATFORM"), isNull()))
                .thenReturn(false);

        service.create(new SaveGatewayAccessRuleCommand("/ops/**", "get", null, null, "运维"));

        verify(gatewayWhitelistRepository).save(org.mockito.ArgumentMatchers.argThat(rule ->
                "GET".equals(rule.getHttpMethod()) && rule.getStatus() == 1 && "MANUAL".equals(rule.getSource())));
    }

    @Test
    @DisplayName("create：同路径 + 方法已存在时抛业务异常且不落库")
    void shouldRejectDuplicateRuleOnCreate() {
        when(gatewayWhitelistRepository.existsRule(eq("/ops/**"), eq("*"), eq("PLATFORM"), isNull()))
                .thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(new SaveGatewayAccessRuleCommand("/ops/**", null, 1, 0, null)));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(gatewayWhitelistRepository, never()).save(any());
    }

    @Test
    @DisplayName("update：条目不存在时抛业务异常")
    void shouldRejectUpdateWhenRuleMissing() {
        when(gatewayWhitelistRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(BizException.class,
                () -> service.update(9L, new SaveGatewayAccessRuleCommand("/ops/**", null, 1, 0, null)));
        verify(gatewayWhitelistRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete：存在时执行物理删除，不存在时抛业务异常")
    void shouldDeleteExistingRuleOnly() {
        when(gatewayWhitelistRepository.findById(9L))
                .thenReturn(Optional.of(GatewayWhitelistAggregate.reconstitute(9L, "/ops/**", "*",
                        "PLATFORM", 1, 0)));
        service.delete(9L);
        verify(gatewayWhitelistRepository).deleteById(9L);

        when(gatewayWhitelistRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.delete(10L));
        verify(gatewayWhitelistRepository, never()).deleteById(10L);
    }
}
