package com.bluewhale.gateway.route;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nacos 动态路由定义仓库（T5）。
 *
 * <p>从 Nacos 配置 {@code whale-gateway-routes.yaml}（分组 {@code DEFAULT_GROUP}）读取动态路由定义，
 * 期望结构为 {@code whale.gateway.dynamic-routes} 列表，条目形如
 * {@code {id, uri, predicates:[{name,args}], filters:[{name,args}]}}；内容以 YAML 为准（JSON 亦兼容，
 * 因 YAML 为 JSON 的超集）。</p>
 *
 * <p><b>可用性优先</b>：Nacos 配置管理器不可用、读取异常或内容解析失败时，均返回空列表并告警，
 * 绝不抛出异常以阻断网关启动；本地静态路由（{@code spring.cloud.gateway...routes}）仍作为兜底生效。</p>
 *
 * <p><b>刷新</b>：监听 {@link EnvironmentChangeEvent}（由 {@code spring.config.import} 的 Nacos 配置
 * 刷新触发），收到后发布 {@link RefreshRoutesEvent}，促使网关重新调用 {@link #getRouteDefinitions()}
 * 从 Nacos 重载动态路由。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository, ApplicationListener<EnvironmentChangeEvent> {

    /** Nacos 动态路由配置 dataId，须与 {@code spring.config.import} 中一致 */
    public static final String DATA_ID = "whale-gateway-routes.yaml";

    /** Nacos 动态路由配置分组 */
    public static final String GROUP = "DEFAULT_GROUP";

    /** Nacos 读取配置的超时时间（毫秒） */
    private static final long READ_TIMEOUT_MILLIS = 3000L;

    /** 动态路由配置根节点键 */
    private static final String ROOT_KEY = "whale";

    /** 动态路由配置第二级键 */
    private static final String GATEWAY_KEY = "gateway";

    /** 动态路由列表键 */
    private static final String ROUTES_KEY = "dynamic-routes";

    /** 路由条目字段：路由 id */
    private static final String FIELD_ID = "id";

    /** 路由条目字段：目标地址 */
    private static final String FIELD_URI = "uri";

    /** 路由条目字段：断言列表 */
    private static final String FIELD_PREDICATES = "predicates";

    /** 路由条目字段：过滤器列表 */
    private static final String FIELD_FILTERS = "filters";

    /** 断言/过滤器通用字段：名称 */
    private static final String FIELD_NAME = "name";

    /** 断言/过滤器通用字段：参数 */
    private static final String FIELD_ARGS = "args";

    /** 动态路由配置被整体拒绝的计数指标名（G3） */
    private static final String METER_REJECTED = "gateway.routes.rejected";

    /** 指标 dataId 标签名 */
    private static final String TAG_DATA_ID = "dataId";

    private final ObjectProvider<NacosConfigManager> configManagerProvider;

    private final ApplicationEventPublisher eventPublisher;

    /** 指标注册表提供者：容器无 MeterRegistry 时返回 {@code null}，指标静默跳过 */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** 当前生效的路由快照（最近一次校验通过的配置） */
    private volatile List<RouteDefinition> currentRoutes = List.of();

    /** 上一版路由快照（供 {@link #rollback()} 回滚） */
    private volatile List<RouteDefinition> previousRoutes = List.of();

    /**
     * 回滚时被放弃的路由签名（{@code null} 表示当前不处于回滚固定状态）。
     *
     * <p>回滚后 Nacos 中仍是新版配置，刷新会再次读到新版并把回滚结果覆盖回去；
     * 记录被放弃版本的签名后，只要 Nacos 内容仍与该签名一致，就保持回滚后的快照不变，
     * 直到配置真正发生新的变更才清除该标记。</p>
     */
    private volatile List<String> abandonedSignatures;

    /** 因校验失败被整体拒绝的配置次数（G3 指标） */
    private final AtomicLong rejectedCount = new AtomicLong();

    /**
     * 构造 Nacos 动态路由仓库
     *
     * @param configManagerProvider Nacos 配置管理器提供者（允许缺失，缺失时动态路由降级为空）
     * @param eventPublisher        应用事件发布器，用于发布路由刷新事件
     * @param meterRegistryProvider Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     */
    public NacosRouteDefinitionRepository(ObjectProvider<NacosConfigManager> configManagerProvider,
                                          ApplicationEventPublisher eventPublisher,
                                          ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.configManagerProvider = configManagerProvider;
        this.eventPublisher = eventPublisher;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * 返回 Nacos 中的动态路由定义；读取或解析失败时返回空列表（不抛异常）
     *
     * @return 动态路由定义流；无配置或异常时为空流
     */
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(loadRouteDefinitions());
    }

    /**
     * 只读仓库不支持保存路由定义，直接返回不支持错误
     *
     * @param route 待保存的路由定义
     * @return 始终错误的信号，表明该仓库为只读
     */
    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.error(new UnsupportedOperationException("Nacos 动态路由仓库为只读，不支持保存路由定义"));
    }

    /**
     * 只读仓库不支持删除路由定义，直接返回不支持错误
     *
     * @param routeId 待删除的路由 id
     * @return 始终错误的信号，表明该仓库为只读
     */
    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.error(new UnsupportedOperationException("Nacos 动态路由仓库为只读，不支持删除路由定义"));
    }

    /**
     * 监听配置变更事件，发布路由刷新事件以重载 Nacos 动态路由
     *
     * @param event 环境配置变更事件
     */
    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        log.info("接收到配置变更事件，发布网关路由刷新事件以重载 Nacos 动态路由");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }

    /**
     * 读取并转换 Nacos 动态路由配置，读取异常时告警并返回空列表
     *
     * @return 动态路由定义列表；不可用时为空列表
     */
    private List<RouteDefinition> loadRouteDefinitions() {
        String content;
        try {
            content = readRawContent();
        } catch (Exception e) {
            log.warn("读取 Nacos 动态路由配置失败（不阻断启动）: dataId={}, reason={}", DATA_ID, e.getMessage());
            return List.of();
        }
        List<RouteDefinition> parsed = convert(content);
        if (parsed.isEmpty()) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        for (RouteDefinition definition : parsed) {
            errors.addAll(RouteDefinitionValidator.validate(definition));
        }
        if (!errors.isEmpty()) {
            long rejected = rejectedCount.incrementAndGet();
            countRejected();
            log.warn("Nacos 动态路由配置校验失败，整体拒绝并保留上一版: dataId={}, rejectedCount={}, errors={}",
                    DATA_ID, rejected, errors);
            return currentRoutes;
        }
        if (isAbandoned(parsed)) {
            log.info("Nacos 动态路由仍为回滚前被放弃的版本，保持回滚后快照: dataId={}, routeCount={}",
                    DATA_ID, currentRoutes.size());
            return currentRoutes;
        }
        if (sameAsCurrent(parsed)) {
            log.debug("Nacos 动态路由配置未变化，保持当前快照: dataId={}, routeCount={}", DATA_ID, currentRoutes.size());
            return currentRoutes;
        }
        previousRoutes = currentRoutes;
        currentRoutes = List.copyOf(parsed);
        abandonedSignatures = null;
        return currentRoutes;
    }

    /**
     * 判断本次解析结果是否为回滚时被放弃的版本（用于回滚后固定快照）。
     *
     * @param parsed 本次解析得到的路由定义列表
     * @return 处于回滚固定状态且内容与被放弃版本一致时返回 {@code true}
     */
    private boolean isAbandoned(List<RouteDefinition> parsed) {
        List<String> abandoned = abandonedSignatures;
        if (abandoned == null) {
            return false;
        }
        if (parsed.size() != abandoned.size()) {
            return false;
        }
        for (int i = 0; i < parsed.size(); i++) {
            if (!signature(parsed.get(i)).equals(abandoned.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断本次解析结果与当前生效快照是否等价（避免同一配置重复刷新时把上一版快照覆盖掉）。
     *
     * @param parsed 本次解析得到的路由定义列表
     * @return 与 {@link #currentRoutes} 等价返回 {@code true}
     */
    private boolean sameAsCurrent(List<RouteDefinition> parsed) {
        if (parsed.size() != currentRoutes.size()) {
            return false;
        }
        for (int i = 0; i < parsed.size(); i++) {
            if (!signature(parsed.get(i)).equals(signature(currentRoutes.get(i)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 生成路由定义的比对签名（id / uri / 断言 / 过滤器及其参数）。
     *
     * @param definition 路由定义
     * @return 比对签名
     */
    private static String signature(RouteDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.getId()).append('|').append(definition.getUri());
        for (PredicateDefinition predicate : definition.getPredicates()) {
            builder.append('|').append(predicate.getName()).append(predicate.getArgs());
        }
        for (FilterDefinition filter : definition.getFilters()) {
            builder.append('|').append(filter.getName()).append(filter.getArgs());
        }
        return builder.toString();
    }

    /**
     * 获取当前生效的动态路由快照。
     *
     * @return 当前路由定义列表（不可变）
     */
    public List<RouteDefinition> getCurrentRoutes() {
        return currentRoutes;
    }

    /**
     * 获取上一版动态路由快照（用于治理端点展示与回滚）。
     *
     * @return 上一版路由定义列表（不可变）
     */
    public List<RouteDefinition> getPreviousRoutes() {
        return previousRoutes;
    }

    /**
     * 获取因校验失败被整体拒绝的配置次数。
     *
     * @return 拒绝次数
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * 记录一次动态路由配置被整体拒绝的指标（容器无 {@link MeterRegistry} 时静默跳过）
     */
    private void countRejected() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METER_REJECTED, TAG_DATA_ID, DATA_ID).increment();
    }

    /**
     * 回滚到上一版路由快照并触发网关重载。
     *
     * <p>回滚后被放弃的版本仍留在 Nacos 中，因此记录其签名；后续刷新只要读到同一版本
     * 就保持回滚后的快照，避免回滚结果被立即覆盖。Nacos 内容发生新的变更后标记自动失效。</p>
     *
     * @return 无上一版可回滚时返回 {@code false}
     */
    public synchronized boolean rollback() {
        if (previousRoutes.isEmpty()) {
            log.warn("无上一版动态路由快照，回滚请求被忽略: dataId={}", DATA_ID);
            return false;
        }
        List<RouteDefinition> abandoned = currentRoutes;
        List<RouteDefinition> target = previousRoutes;
        abandonedSignatures = abandoned.stream().map(NacosRouteDefinitionRepository::signature).toList();
        previousRoutes = abandoned;
        currentRoutes = target;
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        log.info("已回滚到上一版动态路由快照，路由数={}，被放弃路由数={}", currentRoutes.size(), abandoned.size());
        return true;
    }

    /**
     * 从 Nacos 读取动态路由配置的原始文本
     *
     * @return 配置原始文本；Nacos 配置管理器不可用时返回 {@code null}
     * @throws NacosException 读取 Nacos 配置失败时抛出
     */
    private String readRawContent() throws NacosException {
        NacosConfigManager manager = configManagerProvider.getIfAvailable();
        if (manager == null) {
            log.warn("Nacos 配置管理器不可用，跳过动态路由加载: dataId={}", DATA_ID);
            return null;
        }
        ConfigService configService = manager.getConfigService();
        return configService.getConfig(DATA_ID, GROUP, READ_TIMEOUT_MILLIS);
    }

    /**
     * 将配置文本解析并转换为路由定义列表；空内容、结构不符或解析失败时返回空列表
     *
     * @param content Nacos 配置原始文本（YAML 或 JSON），可能为 {@code null} 或空白
     * @return 动态路由定义列表；无法解析时为空列表
     */
    List<RouteDefinition> convert(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Object parsed;
        try {
            parsed = new Yaml().load(content);
        } catch (Exception e) {
            log.warn("解析 Nacos 动态路由配置失败（忽略该配置）: dataId={}, reason={}", DATA_ID, e.getMessage());
            return List.of();
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            log.warn("Nacos 动态路由配置根节点非键值结构，忽略: dataId={}", DATA_ID);
            return List.of();
        }
        Object routesNode = navigateToRoutes(root);
        if (!(routesNode instanceof List<?> routeList)) {
            return List.of();
        }
        List<RouteDefinition> definitions = new ArrayList<>();
        for (Object item : routeList) {
            RouteDefinition definition = toRouteDefinition(item);
            if (definition != null) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    /**
     * 按 {@code whale.gateway.dynamic-routes} 层级定位动态路由列表节点
     *
     * @param root 配置根节点
     * @return 动态路由列表节点；层级缺失时返回 {@code null}
     */
    private static Object navigateToRoutes(Map<?, ?> root) {
        Object whale = root.get(ROOT_KEY);
        if (!(whale instanceof Map<?, ?> whaleMap)) {
            return null;
        }
        Object gateway = whaleMap.get(GATEWAY_KEY);
        if (!(gateway instanceof Map<?, ?> gatewayMap)) {
            return null;
        }
        return gatewayMap.get(ROUTES_KEY);
    }

    /**
     * 将单个路由条目转换为 {@link RouteDefinition}，缺失 id/uri 或 uri 非法时跳过
     *
     * @param item 路由条目节点
     * @return 路由定义；条目非法时返回 {@code null}
     */
    private RouteDefinition toRouteDefinition(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            log.warn("动态路由条目非键值结构，跳过: {}", item);
            return null;
        }
        String id = stringValue(map.get(FIELD_ID));
        String uri = stringValue(map.get(FIELD_URI));
        if (id == null || id.isBlank() || uri == null || uri.isBlank()) {
            log.warn("动态路由缺少 id/uri，跳过: id={}, uri={}", id, uri);
            return null;
        }
        RouteDefinition definition = new RouteDefinition();
        definition.setId(id);
        try {
            definition.setUri(URI.create(uri));
        } catch (IllegalArgumentException e) {
            log.warn("动态路由 uri 非法，跳过: id={}, uri={}", id, uri);
            return null;
        }
        definition.setPredicates(toPredicates(map.get(FIELD_PREDICATES)));
        definition.setFilters(toFilters(map.get(FIELD_FILTERS)));
        return definition;
    }

    /**
     * 将断言节点列表转换为断言定义列表，缺少名称的条目跳过
     *
     * @param node 断言节点
     * @return 断言定义列表；节点非列表时为空列表
     */
    private static List<PredicateDefinition> toPredicates(Object node) {
        List<PredicateDefinition> predicates = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return predicates;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = stringValue(map.get(FIELD_NAME));
            if (name == null || name.isBlank()) {
                continue;
            }
            PredicateDefinition predicate = new PredicateDefinition();
            predicate.setName(name);
            predicate.setArgs(toArgs(map.get(FIELD_ARGS)));
            predicates.add(predicate);
        }
        return predicates;
    }

    /**
     * 将过滤器节点列表转换为过滤器定义列表，缺少名称的条目跳过
     *
     * @param node 过滤器节点
     * @return 过滤器定义列表；节点非列表时为空列表
     */
    private static List<FilterDefinition> toFilters(Object node) {
        List<FilterDefinition> filters = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return filters;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = stringValue(map.get(FIELD_NAME));
            if (name == null || name.isBlank()) {
                continue;
            }
            FilterDefinition filter = new FilterDefinition();
            filter.setName(name);
            filter.setArgs(toArgs(map.get(FIELD_ARGS)));
            filters.add(filter);
        }
        return filters;
    }

    /**
     * 将参数节点转换为字符串键值映射（忽略空键或空值）
     *
     * @param node 参数节点
     * @return 参数字符串映射；节点非键值结构时为空映射
     */
    private static Map<String, String> toArgs(Object node) {
        Map<String, String> args = new LinkedHashMap<>();
        if (!(node instanceof Map<?, ?> argsMap)) {
            return args;
        }
        for (Map.Entry<?, ?> entry : argsMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            args.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return args;
    }

    /**
     * 将节点值安全转为字符串，{@code null} 原样返回
     *
     * @param value 节点值
     * @return 字符串形式的值；{@code null} 入参返回 {@code null}
     */
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
