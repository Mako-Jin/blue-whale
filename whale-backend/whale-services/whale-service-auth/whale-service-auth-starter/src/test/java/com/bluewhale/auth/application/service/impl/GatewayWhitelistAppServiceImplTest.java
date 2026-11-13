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
 * {@link GatewayWhitelistAppServiceImpl} 单元测试：映射、顺序、停用项过滤与租户维度。
 *
 * @author BlueWhale
 */
class GatewayWhitelistAppServiceImplTest {

    private final GatewayWhitelistRepository gatewayWhitelistRepository = mock(GatewayWhitelistRepository.class);

    private final GatewayWhitelistAppServiceImpl service =
            new GatewayWhitelistAppServiceImpl(gatewayWhitelistRepository);

    @Test
    @DisplayName("listEnabled：按仓储顺序映射路径、方法与租户维度")
    void shouldMapEnabledRulesInOrder() {
        when(gatewayWhitelistRepository.findAllEnabled()).thenReturn(List.of(
                GatewayWhitelistAggregate.reconstitute(1L, "/auth/v1/login", "*", "PLATFORM", 0L, 1, 1),
                GatewayWhitelistAggregate.reconstitute(2L, "/music/stream/**", null, "TENANT", 5L, 1, 2)));

        List<GatewayWhitelistVO> result = service.listEnabled();

        assertEquals(2, result.size());
        assertEquals("/auth/v1/login", result.get(0).pathPattern());
        assertEquals("*", result.get(0).httpMethod());
        assertEquals("PLATFORM", result.get(0).scope());
        assertEquals(0L, result.get(0).tenantId());
        // 方法为空时归一为通配符
        assertEquals("*", result.get(1).httpMethod());
        assertEquals("TENANT", result.get(1).scope());
        assertEquals(5L, result.get(1).tenantId());
    }

    @Test
    @DisplayName("listEnabled：停用项被过滤")
    void shouldFilterDisabledRules() {
        when(gatewayWhitelistRepository.findAllEnabled()).thenReturn(List.of(
                GatewayWhitelistAggregate.reconstitute(1L, "/auth/v1/login", "*", "PLATFORM", 0L, 0, 1)));

        assertTrue(service.listEnabled().isEmpty());
    }

    @Test
    @DisplayName("list：透传状态过滤并映射管理字段")
    void shouldListAllRulesWithStatusFilter() {
        GatewayWhitelistAggregate rule = GatewayWhitelistAggregate.reconstitute(1L, "/auth/v1/login", null,
                "PLATFORM", 0L, "MIGRATION", "登录放行", 0, 1, null, null);
        when(gatewayWhitelistRepository.findAll(0)).thenReturn(List.of(rule));

        List<GatewayAccessRuleVO> result = service.list(0);

        assertEquals(1, result.size());
        assertEquals("MIGRATION", result.get(0).source());
        assertEquals(0, result.get(0).status());
        assertEquals("登录放行", result.get(0).remark());
        assertEquals(0L, result.get(0).tenantId());
        verify(gatewayWhitelistRepository).findAll(0);
    }

    @Test
    @DisplayName("create：方法归一为大写并落库（平台级）")
    void shouldNormalizeMethodOnCreate() {
        when(gatewayWhitelistRepository.existsRule(eq("/ops/**"), eq("GET"), eq("PLATFORM"), eq(0L), isNull()))
                .thenReturn(false);

        service.create(new SaveGatewayAccessRuleCommand("/ops/**", "get", null, null, null, null, "运维"));

        verify(gatewayWhitelistRepository).save(org.mockito.ArgumentMatchers.argThat(rule ->
                "GET".equals(rule.getHttpMethod()) && rule.getStatus() == 1 && "MANUAL".equals(rule.getSource())));
    }

    @Test
    @DisplayName("create：租户级条目绑定租户 ID 落库")
    void shouldCreateTenantScopedRule() {
        when(gatewayWhitelistRepository.existsRule(eq("/report/**"), eq("*"), eq("TENANT"), eq(7L), isNull()))
                .thenReturn(false);

        service.create(new SaveGatewayAccessRuleCommand("/report/**", null, "TENANT", 7L, 1, null, "租户报表轮询"));

        verify(gatewayWhitelistRepository).save(org.mockito.ArgumentMatchers.argThat(rule ->
                "TENANT".equals(rule.getScope()) && Long.valueOf(7L).equals(rule.getTenantId())));
    }

    @Test
    @DisplayName("create：同路径 + 方法 + 作用域 + 租户已存在时抛业务异常且不落库")
    void shouldRejectDuplicateRuleOnCreate() {
        when(gatewayWhitelistRepository.existsRule(eq("/ops/**"), eq("*"), eq("PLATFORM"), eq(0L), isNull()))
                .thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(new SaveGatewayAccessRuleCommand("/ops/**", null, null, null, 1, 0, null)));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(gatewayWhitelistRepository, never()).save(any());
    }

    @Test
    @DisplayName("update：条目不存在时抛业务异常")
    void shouldRejectUpdateWhenRuleMissing() {
        when(gatewayWhitelistRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(BizException.class,
                () -> service.update(9L, new SaveGatewayAccessRuleCommand("/ops/**", null, null, null, 1, 0, null)));
        verify(gatewayWhitelistRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete：存在时执行物理删除，不存在时抛业务异常")
    void shouldDeleteExistingRuleOnly() {
        when(gatewayWhitelistRepository.findById(9L))
                .thenReturn(Optional.of(GatewayWhitelistAggregate.reconstitute(9L, "/ops/**", "*",
                        "PLATFORM", 0L, 1, 0)));
        service.delete(9L);
        verify(gatewayWhitelistRepository).deleteById(9L);

        when(gatewayWhitelistRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.delete(10L));
        verify(gatewayWhitelistRepository, never()).deleteById(10L);
    }
}
