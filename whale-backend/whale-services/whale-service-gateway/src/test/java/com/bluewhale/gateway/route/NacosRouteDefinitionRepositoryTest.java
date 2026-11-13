package com.bluewhale.gateway.route;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    /** 测试专用兜底路由文件位置 */
    private static final String TEST_FALLBACK_LOCATION = "classpath:gateway-routes-fallback-test.yaml";

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

    /** 带 order 的路由配置样例 */
    private static final String ORDER_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-demo
                    uri: lb://whale-demo
                    order: -5
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo/**
                    filters: []
            """;

    /** 同一路由仅调整 order 的样例（用于验证签名包含 order） */
    private static final String ORDER_CONTENT_V2 = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-demo
                    uri: lb://whale-demo
                    order: 10
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo/**
                    filters: []
            """;

    /** order 非整数的样例 */
    private static final String INVALID_ORDER_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-bad-order
                    uri: lb://whale-bad-order
                    order: high
                    predicates:
                      - name: Path
                        args:
                          patterns: /bad/**
                    filters: []
            """;

    /** id 重复的样例 */
    private static final String DUPLICATE_ID_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-demo
                    uri: lb://whale-demo
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo/**
                    filters: []
                  - id: whale-demo
                    uri: lb://whale-demo2
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo2/**
                    filters: []
            """;

    /** 无法解析的非法内容样例 */
    private static final String INVALID_CONTENT = "{ \"whale\": { \"gateway\": {";

    /** 校验通过的动态路由配置样例（用于治理快照） */
    private static final String VALID_CONTENT = YAML_CONTENT;

    /** 第二版校验通过的配置样例（用于滚动快照与回滚） */
    private static final String SECOND_VALID_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-demo2
                    uri: lb://whale-demo2
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo2/**
                    filters: []
            """;

    /** 校验不通过的配置样例：Path 全域匹配 */
    private static final String ILLEGAL_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-bad
                    uri: lb://whale-bad
                    predicates:
                      - name: Path
                        args:
                          patterns: /**
                    filters: []
            """;

    /** 回滚后重新发布的新配置样例（用于验证回滚标记随新版本失效） */
    private static final String THIRD_VALID_CONTENT = """
            whale:
              gateway:
                dynamic-routes:
                  - id: whale-demo3
                    uri: lb://whale-demo3
                    predicates:
                      - name: Path
                        args:
                          patterns: /demo3/**
                    filters: []
            """;

    @Mock
    private ObjectProvider<NacosConfigManager> configManagerProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NacosConfigManager configManager;

    @Mock
    private ConfigService configService;

    @Mock
    private RouteOverrideProvider routeOverrideProvider;

    private NacosRouteDefinitionRepository repository;

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        // 既有用例只关注 Nacos 侧行为，关闭兜底路由以隔离断言；
        // 覆盖提供者默认桩（isOverrideEnabled=false）不参与路由过滤
        repository = createRepository(NacosRouteDefinitionRepository.DEFAULT_FALLBACK_LOCATION, false);
    }

    /**
     * 构建被测仓库
     *
     * @param fallbackLocation 兜底路由文件位置
     * @param fallbackEnabled  是否启用兜底路由
     * @return 被测仓库实例
     */
    private NacosRouteDefinitionRepository createRepository(String fallbackLocation, boolean fallbackEnabled) {
        return new NacosRouteDefinitionRepository(configManagerProvider,
                new StubProvider<>(routeOverrideProvider), eventPublisher,
                new StubProvider<>(registry), fallbackLocation, fallbackEnabled);
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
    @DisplayName("convert：解析 order 字段，缺省为默认 0")
    void shouldParseOrder() {
        List<RouteDefinition> ordered = repository.convert(ORDER_CONTENT);
        assertEquals(1, ordered.size());
        assertEquals(-5, ordered.get(0).getOrder());

        List<RouteDefinition> defaulted = repository.convert(YAML_CONTENT);
        assertEquals(1, defaulted.size());
        assertEquals(0, defaulted.get(0).getOrder());
    }

    @Test
    @DisplayName("convert：order 非整数时跳过该条目")
    void shouldSkipInvalidOrder() {
        assertTrue(repository.convert(INVALID_ORDER_CONTENT).isEmpty());
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
    @DisplayName("getRouteDefinitions：id 重复时整体拒绝并计数")
    void shouldRejectDuplicateRouteIds() throws Exception {
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(DUPLICATE_ID_CONTENT);

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertTrue(definitions.isEmpty());
        assertTrue(repository.getCurrentRoutes().isEmpty());
        assertEquals(1, repository.getRejectedCount());
    }

    @Test
    @DisplayName("getRouteDefinitions：仅 order 变化时也刷新快照")
    void shouldRefreshWhenOrderChanges() throws Exception {
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(ORDER_CONTENT);

        repository.getRouteDefinitions().collectList().block();
        assertEquals(-5, repository.getCurrentRoutes().get(0).getOrder());

        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(ORDER_CONTENT_V2);
        repository.getRouteDefinitions().collectList().block();
        assertEquals(10, repository.getCurrentRoutes().get(0).getOrder());
    }

    @Test
    @DisplayName("兜底路由：Nacos 不可用时读取 classpath 兜底文件")
    void shouldLoadFallbackWhenNacosUnavailable() {
        NacosRouteDefinitionRepository fallbackRepository =
                createRepository(TEST_FALLBACK_LOCATION, true);
        when(configManagerProvider.getIfAvailable()).thenReturn(null);

        List<RouteDefinition> definitions = fallbackRepository.getRouteDefinitions().collectList().block();

        assertEquals(2, definitions.size());
        assertEquals("fallback-demo", definitions.get(0).getId());
        assertEquals(100, definitions.get(0).getOrder());
        assertEquals("whale-demo", definitions.get(1).getId());
        assertEquals("lb://whale-fallback", definitions.get(1).getUri().toString());
    }

    @Test
    @DisplayName("兜底路由：Nacos 同 id 路由覆盖兜底，其余兜底保留")
    void shouldOverrideFallbackByIdFromNacos() throws Exception {
        NacosRouteDefinitionRepository fallbackRepository =
                createRepository(TEST_FALLBACK_LOCATION, true);
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(YAML_CONTENT);

        List<RouteDefinition> definitions = fallbackRepository.getRouteDefinitions().collectList().block();

        assertEquals(2, definitions.size());
        assertEquals("fallback-demo", definitions.get(0).getId());
        assertEquals("whale-demo", definitions.get(1).getId());
        assertEquals("lb://whale-demo", definitions.get(1).getUri().toString(), "Nacos 应覆盖同 id 兜底路由");
    }

    @Test
    @DisplayName("兜底路由：生产兜底文件可解析且通过校验")
    void shouldParseProductionFallback() throws Exception {
        String content = new ClassPathResource("gateway-routes-fallback.yaml")
                .getContentAsString(StandardCharsets.UTF_8);

        List<RouteDefinition> routes = repository.convert(content);

        assertEquals(55, routes.size());
        assertTrue(RouteDefinitionValidator.validateAll(routes).isEmpty());
    }

    @Test
    @DisplayName("onApplicationEvent：配置变更时发布路由刷新事件")
    void shouldPublishRefreshEventOnConfigChange() {
        repository.onApplicationEvent(new EnvironmentChangeEvent(Set.of("whale.gateway.dynamic-routes[0].id")));

        verify(eventPublisher).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    @DisplayName("G3 治理：非法配置整体拒绝，保留当前生效路由并累加拒绝计数")
    void shouldRejectIllegalConfigAndKeepCurrentRoutes() throws Exception {
        stubConfig(VALID_CONTENT, ILLEGAL_CONTENT);

        List<RouteDefinition> first = repository.getRouteDefinitions().collectList().block();
        assertEquals(1, first.size());
        assertEquals("whale-demo", first.get(0).getId());

        List<RouteDefinition> second = repository.getRouteDefinitions().collectList().block();

        assertEquals(1, second.size());
        assertEquals("whale-demo", second.get(0).getId(), "非法配置不应覆盖当前路由");
        assertEquals(1, repository.getRejectedCount());
        assertEquals(1, repository.getCurrentRoutes().size());
        assertEquals(1.0d, registry.find("gateway.routes.rejected")
                .tag("dataId", NacosRouteDefinitionRepository.DATA_ID).counter().count());
    }

    @Test
    @DisplayName("G3 治理：首版配置即非法时返回空路由且拒绝计数 +1")
    void shouldReturnEmptyWhenFirstConfigIllegal() throws Exception {
        stubConfig(ILLEGAL_CONTENT);

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertTrue(definitions.isEmpty());
        assertEquals(1, repository.getRejectedCount());
        assertTrue(repository.getPreviousRoutes().isEmpty());
    }

    @Test
    @DisplayName("G3 治理：连续合法配置滚动快照，回滚恢复上一版并发布刷新事件")
    void shouldRollbackToPreviousSnapshot() throws Exception {
        stubConfig(VALID_CONTENT, SECOND_VALID_CONTENT);

        repository.getRouteDefinitions().collectList().block();
        List<RouteDefinition> latest = repository.getRouteDefinitions().collectList().block();
        assertEquals("whale-demo2", latest.get(0).getId());
        assertEquals("whale-demo", repository.getPreviousRoutes().get(0).getId());

        assertTrue(repository.rollback());

        assertEquals("whale-demo", repository.getCurrentRoutes().get(0).getId());
        assertEquals("whale-demo2", repository.getPreviousRoutes().get(0).getId());
        verify(eventPublisher).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    @DisplayName("G3 治理：同名配置重复刷新不覆盖上一版快照（保证回滚有效）")
    void shouldKeepPreviousSnapshotWhenSameConfigReloaded() throws Exception {
        stubConfig(VALID_CONTENT, SECOND_VALID_CONTENT, SECOND_VALID_CONTENT);

        repository.getRouteDefinitions().collectList().block();
        repository.getRouteDefinitions().collectList().block();
        repository.getRouteDefinitions().collectList().block();

        assertEquals("whale-demo2", repository.getCurrentRoutes().get(0).getId());
        assertEquals("whale-demo", repository.getPreviousRoutes().get(0).getId());
        assertTrue(repository.rollback());
        assertEquals("whale-demo", repository.getCurrentRoutes().get(0).getId());
    }

    @Test
    @DisplayName("G3 治理：回滚后 Nacos 仍是被放弃版本时，刷新不得覆盖回滚结果")
    void shouldKeepRollbackResultWhenAbandonedVersionStillPublished() throws Exception {
        stubConfig(VALID_CONTENT, SECOND_VALID_CONTENT);

        repository.getRouteDefinitions().collectList().block();
        repository.getRouteDefinitions().collectList().block();
        assertEquals("whale-demo2", repository.getCurrentRoutes().get(0).getId());

        assertTrue(repository.rollback());
        assertEquals("whale-demo", repository.getCurrentRoutes().get(0).getId());

        List<RouteDefinition> refreshed = repository.getRouteDefinitions().collectList().block();

        assertEquals("whale-demo", refreshed.get(0).getId(), "被放弃版本仍在 Nacos 时不应覆盖回滚结果");
        assertEquals("whale-demo", repository.getCurrentRoutes().get(0).getId());
        assertEquals("whale-demo2", repository.getPreviousRoutes().get(0).getId());
    }

    @Test
    @DisplayName("G3 治理：回滚后发布真正的新配置时恢复正常滚动快照")
    void shouldAdoptNewConfigAfterRollback() throws Exception {
        stubConfig(VALID_CONTENT, SECOND_VALID_CONTENT, THIRD_VALID_CONTENT);

        repository.getRouteDefinitions().collectList().block();
        repository.getRouteDefinitions().collectList().block();
        assertTrue(repository.rollback());
        assertEquals("whale-demo", repository.getCurrentRoutes().get(0).getId());

        List<RouteDefinition> refreshed = repository.getRouteDefinitions().collectList().block();

        assertEquals("whale-demo3", refreshed.get(0).getId());
        assertEquals("whale-demo3", repository.getCurrentRoutes().get(0).getId());
        assertEquals("whale-demo", repository.getPreviousRoutes().get(0).getId());
    }

    @Test
    @DisplayName("G3 治理：无可回滚快照时回滚被忽略且不发布刷新事件")
    void shouldIgnoreRollbackWithoutPreviousSnapshot() {
        assertFalse(repository.rollback());
        assertTrue(repository.getCurrentRoutes().isEmpty());
        verify(eventPublisher, never()).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    @DisplayName("GW-10 P2 覆盖：停用路由从生效列表中移除")
    void shouldFilterDisabledRouteByOverride() throws Exception {
        stubConfig(VALID_CONTENT);
        when(routeOverrideProvider.isOverrideEnabled()).thenReturn(true);
        when(routeOverrideProvider.overrides()).thenReturn(Map.of("whale-demo",
                new RouteOverrideRule("whale-demo", false, null, null, 1L)));

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertTrue(definitions.isEmpty(), "停用覆盖应使路由全站下线");
    }

    @Test
    @DisplayName("GW-10 P2 覆盖：生效窗口外的路由移除，窗口内保留")
    void shouldFilterRouteByEffectiveWindow() throws Exception {
        stubConfig(VALID_CONTENT);
        when(routeOverrideProvider.isOverrideEnabled()).thenReturn(true);
        LocalDateTime now = LocalDateTime.now();
        when(routeOverrideProvider.overrides()).thenReturn(Map.of("whale-demo",
                new RouteOverrideRule("whale-demo", true, now.plusSeconds(3600), null, 2L)));

        List<RouteDefinition> before = repository.getRouteDefinitions().collectList().block();
        assertTrue(before.isEmpty(), "窗口未开始时路由不应生效");

        when(routeOverrideProvider.overrides()).thenReturn(Map.of("whale-demo",
                new RouteOverrideRule("whale-demo", true, now.minusSeconds(3600), now.plusSeconds(3600), 3L)));
        List<RouteDefinition> inside = repository.getRouteDefinitions().collectList().block();
        assertEquals(1, inside.size(), "窗口内路由应恢复生效");
    }

    @Test
    @DisplayName("GW-10 P2 覆盖：孤儿 route_id 忽略且不阻断刷新，按集合变化计一次指标")
    void shouldIgnoreOrphanOverrideAndCountOnce() throws Exception {
        stubConfig(VALID_CONTENT, VALID_CONTENT);
        when(routeOverrideProvider.isOverrideEnabled()).thenReturn(true);
        when(routeOverrideProvider.overrides()).thenReturn(Map.of("not-exist",
                new RouteOverrideRule("not-exist", false, null, null, 1L)));

        List<RouteDefinition> first = repository.getRouteDefinitions().collectList().block();
        List<RouteDefinition> second = repository.getRouteDefinitions().collectList().block();

        assertEquals(1, first.size());
        assertEquals(1, second.size(), "孤儿覆盖不应阻断路由刷新");
        Counter counter = registry.find("gateway.routes.override").tag("result", "orphan").counter();
        assertNotNull(counter, "孤儿条目应计指标");
        assertEquals(1.0d, counter.count(), "孤儿集合未变化时不重复计数");
    }

    @Test
    @DisplayName("GW-10 P2 覆盖：覆盖层关闭时不过滤路由")
    void shouldSkipOverrideWhenDisabled() throws Exception {
        stubConfig(VALID_CONTENT);
        // isOverrideEnabled=false 时路由仓库不应触碰覆盖快照
        when(routeOverrideProvider.isOverrideEnabled()).thenReturn(false);

        List<RouteDefinition> definitions = repository.getRouteDefinitions().collectList().block();

        assertEquals(1, definitions.size());
        verify(routeOverrideProvider, never()).overrides();
    }

    @Test
    @DisplayName("GW-10 P2 覆盖：覆盖提供者缺失时不过滤路由")
    void shouldSkipOverrideWhenProviderMissing() throws Exception {
        stubConfig(VALID_CONTENT);
        NacosRouteDefinitionRepository orphanlessRepository =
                new NacosRouteDefinitionRepository(configManagerProvider, new StubProvider<>(null),
                        eventPublisher, new StubProvider<>(registry),
                        NacosRouteDefinitionRepository.DEFAULT_FALLBACK_LOCATION, false);
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(VALID_CONTENT);

        List<RouteDefinition> definitions = orphanlessRepository.getRouteDefinitions().collectList().block();

        assertEquals(1, definitions.size());
    }

    /**
     * 桩化 Nacos 配置读取，按调用顺序依次返回给定配置内容
     *
     * @param contents 配置内容序列（最后一次之后重复使用最后一个）
     * @throws Exception Nacos 客户端桩设置异常
     */
    private void stubConfig(String... contents) throws Exception {
        when(configManagerProvider.getIfAvailable()).thenReturn(configManager);
        when(configManager.getConfigService()).thenReturn(configService);
        var stubbing = when(configService.getConfig(anyString(), anyString(), anyLong()));
        if (contents.length == 1) {
            stubbing.thenReturn(contents[0]);
            return;
        }
        String[] rest = new String[contents.length - 1];
        System.arraycopy(contents, 1, rest, 0, rest.length);
        stubbing.thenReturn(contents[0], rest);
    }

    /**
     * 测试用 {@link ObjectProvider} 桩：{@code getIfAvailable()} 返回给定实例（可为 {@code null}）。
     *
     * @param <T> 提供的 Bean 类型
     * @author BlueWhale
     */
    private static final class StubProvider<T> implements ObjectProvider<T> {

        /** 待返回的实例，可为 {@code null} */
        private final T value;

        /**
         * 构造提供者桩
         *
         * @param value 待返回的实例，{@code null} 表示不存在
         */
        private StubProvider(T value) {
            this.value = value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }
    }
}
