package com.bluewhale.gateway.route;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NacosRouteDefinitionRepository} 单元测试（T5）：正常解析 YAML/JSON、空配置、非法内容与读取异常
 * 均返回空列表且不抛异常，以及配置变更触发路由刷新事件。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class NacosRouteDefinitionRepositoryTest {

    /** 正常 YAML 配置样例 */
    private static final String YAML_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-demo
                    uri: lb://whale-demo
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo/**
                    filters:
                      - name: AddResponseHeader
                        args:
                          name: X-Demo
                          value: "1"
            """;

    /** 正常 JSON 配置样例（YAML 为 JSON 超集，故应可解析） */
    private static final String JSON_CONTENT = "{\"whale\":{\"gateway\":{\"dynamic-routes\":[{\"id\":\"whale-json\","
            + "\"uri\":\"lb://whale-json\",\"predicates\":[{\"name\":\"Path\",\"args\":{\"patterns\":\"/json/**\"}}],"
            + "\"filters\":[]}]}}}";

    /** 结构合法但无动态路由的样例 */
    private static final String NO_ROUTES_CONTENT = "whale:\n  gateway:\n    other: value\n";

    /** 无法解析的非法内容样例 */
    private static final String INVALID_CONTENT = "{ \"whale\": { \"gateway\": {";

    @Mock
    private ObjectProvider<NacosConfigManager> configManagerProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NacosConfigManager configManager;

    @Mock
    private ConfigService configService;

    private NacosRouteDefinitionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new NacosRouteDefinitionRepository(configManagerProvider, eventPublisher);
    }

    @Test
    @DisplayName("convert：正常 YAML 解析为路由定义（含断言与过滤器）")
    void shouldParseYamlContent() {
        List<RouteDefinition> definitions = repository.convert(YAML_CONTENT);

        assertEquals(1, definitions.size());
        RouteDefinition definition = definitions.get(0);
        assertEquals("whale-demo", definition.getId());
        assertEquals("lb://whale-demo", definition.getUri().toString());
        assertEquals(1, definition.getPredicates().size());
        assertEquals("Path", definition.getPredicates().get(0).getName());
        assertEquals("/demo/**", definition.getPredicates().get(0).getArgs().get("patterns"));
        assertEquals(1, definition.getFilters().size());
        assertEquals("AddResponseHeader", definition.getFilters().get(0).getName());
    }

    @Test
    @DisplayName("convert：正常 JSON 解析为路由定义")
    void shouldParseJsonContent() {
        List<RouteDefinition> definitions = repository.convert(JSON_CONTENT);

        assertEquals(1, definitions.size());
        assertEquals("whale-json", definitions.get(0).getId());
        assertEquals("lb://whale-json", definitions.get(0).getUri().toString());
    }

    @Test
    @DisplayName("convert：空配置（null/空串/空白）返回空列表")
    void shouldReturnEmptyForBlankContent() {
        assertTrue(repository.convert(null).isEmpty());
        assertTrue(repository.convert("").isEmpty());
        assertTrue(repository.convert("   ").isEmpty());
    }

    @Test
    @DisplayName("convert：非法内容返回空列表且不抛异常")
    void shouldReturnEmptyForInvalidContent() {
        assertTrue(repository.convert(INVALID_CONTENT).isEmpty());
    }

    @Test
    @DisplayName("convert：结构合法但无 dynamic-routes 时返回空列表")
    void shouldReturnEmptyWhenNoRoutesNode() {
        assertTrue(repository.convert(NO_ROUTES_CONTENT).isEmpty());
    }

    @Test
    @DisplayName("getRouteDefinitions：Nacos 配置管理器缺失时返回空流且不抛异常")
    void shouldReturnEmptyWhenNoNacosManager() {
        when(configManagerProvider.getIfAvailable()).thenReturn(null);

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertTrue(definitions.isEmpty());
    }

    @Test
    @DisplayName("getRouteDefinitions：读取 Nacos 抛异常时返回空流且不抛异常")
    void shouldReturnEmptyWhenReadFails() {
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenThrow(new RuntimeException("nacos down"));

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertTrue(definitions.isEmpty());
    }

    @Test
    @DisplayName("getRouteDefinitions：正常读取并解析 Nacos 配置")
    void shouldReturnRoutesFromNacos() throws Exception {
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(YAML_CONTENT);

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertEquals(1, definitions.size());
        assertEquals("whale-demo", definitions.get(0).getId());
    }

    @Test
    @DisplayName("onApplicationEvent：配置变更时发布路由刷新事件")
    void shouldPublishRefreshEventOnConfigChange() {
        repository.onApplicationEvent(new EnvironmentChangeEvent(Set.of("whale.gateway.dynamic-routes[0].id")));

        verify(eventPublisher).publishEvent(any(RefreshRoutesEvent.class));
    }
}
