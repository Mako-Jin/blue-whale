package com.bluewhale.gateway.route;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nacos 动态路由定义仓库（T5）。
 *
 * <p>从 Nacos 配置 {@code whale-gateway-routes.yaml}（分组 {@code DEFAULT_GROUP}）读取动态路由定义，
 * 期望结构为 {@code whale.gateway.dynamic-routes} 列表，条目形如
 * {@code {id, uri, predicates:[{name,args}], filters:[{name,args}]}}；内容以 YAML 为准（JSON 亦兼容，
 * 因 YAML 为 JSON 的超集）。</p>
 *
 * <p><b>可用性优先</b>：classpath {@code gateway-routes-fallback.yaml} 提供基础路由冷备
 * （结构与 Nacos 侧 {@code whale.gateway.dynamic-routes} 完全一致），Nacos 动态路由按 id 合并并优先覆盖；
 * Nacos 不可用、读取异常或内容非法时使用兜底或保留上一版快照，绝不抛出异常阻断网关启动。</p>
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

    /** 兜底路由文件默认位置（classpath） */
    public static final String DEFAULT_FALLBACK_LOCATION = "classpath:gateway-routes-fallback.yaml";

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

    /** 路由条目字段：匹配优先级（数值小者优先，可为负） */
    private static final String FIELD_ORDER = "order";

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

    /** 兜底路由文件位置（测试可替换） */
    private final String fallbackLocation;

    /** 是否启用兜底路由（测试可关闭以隔离断言） */
    private final boolean fallbackEnabled;

    /** 兜底路由解析缓存（构建期静态资源，进程内只读） */
    private volatile List<RouteDefinition> fallbackRoutes;

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
     * <p><b>必须显式标注 {@code @Autowired}</b>：本类有"正式"与"测试用（可指定兜底位置）"两个构造器，
     * Spring 在存在多个构造器且无一标注时不会去猜，而是去找无参构造，找不到就直接
     * {@code BeanInstantiationException: No default constructor found} 把网关整个启崩。
     * 早期只有一个构造器时能自动选中，加第二个的同时漏了注解，问题只在"重建镜像"时才暴露。</p>
     *
     * @param configManagerProvider Nacos 配置管理器提供者（允许缺失，缺失时仅使用兜底路由）
     * @param eventPublisher        应用事件发布器，用于发布路由刷新事件
     * @param meterRegistryProvider Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     */
    @Autowired
    public NacosRouteDefinitionRepository(ObjectProvider<NacosConfigManager> configManagerProvider,
                                          ApplicationEventPublisher eventPublisher,
                                          ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(configManagerProvider, eventPublisher, meterRegistryProvider, DEFAULT_FALLBACK_LOCATION, true);
    }

    /**
     * 构造 Nacos 动态路由仓库（可指定兜底路由位置，供测试隔离使用）
     *
     * @param configManagerProvider Nacos 配置管理器提供者（允许缺失，缺失时仅使用兜底路由）
     * @param eventPublisher        应用事件发布器，用于发布路由刷新事件
     * @param meterRegistryProvider Micrometer 指标注册表提供者，缺省时返回 {@code null} 表示不采集指标
     * @param fallbackLocation      兜底路由文件位置（Spring Resource 表达式）
     * @param fallbackEnabled       是否启用兜底路由
     */
    NacosRouteDefinitionRepository(ObjectProvider<NacosConfigManager> configManagerProvider,
                                   ApplicationEventPublisher eventPublisher,
                                   ObjectProvider<MeterRegistry> meterRegistryProvider,
                                   String fallbackLocation, boolean fallbackEnabled) {
        this.configManagerProvider = configManagerProvider;
        this.eventPublisher = eventPublisher;
        this.meterRegistryProvider = meterRegistryProvider;
        this.fallbackLocation = fallbackLocation;
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * 返回兜底路由与 Nacos 动态路由合并后的定义；读取或解析失败时降级（不抛异常）
     *
     * @return 路由定义流；无兜底且无动态路由时为空流
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
     * 读取 Nacos 动态路由并与兜底路由按 id 合并；读取异常/配置非法时降级，不抛异常
     *
     * @return 合并后的路由定义列表；Nacos 不可用时为兜底路由
     */
    private List<RouteDefinition> loadRouteDefinitions() {
        List<RouteDefinition> fallback = loadFallbackRoutes();
        String content;
        try {
            content = readRawContent();
        } catch (Exception e) {
            log.warn("读取 Nacos 动态路由配置失败，使用现有快照或兜底路由（不阻断启动）: dataId={}, reason={}",
                    DATA_ID, e.getMessage());
            return degrade(fallback);
        }
        List<RouteDefinition> parsed = convert(content);
        if (parsed.isEmpty()) {
            return replaceSnapshot(fallback);
        }
        List<String> errors = RouteDefinitionValidator.validateAll(parsed);
        if (!errors.isEmpty()) {
            long rejected = rejectedCount.incrementAndGet();
            countRejected();
            log.warn("Nacos 动态路由配置校验失败，整体拒绝并保留上一版: dataId={}, rejectedCount={}, errors={}",
                    DATA_ID, rejected, errors);
            return degrade(fallback);
        }
        List<RouteDefinition> merged = merge(fallback, parsed);
        List<String> mergedErrors = RouteDefinitionValidator.validateAll(merged);
        if (!mergedErrors.isEmpty()) {
            long rejected = rejectedCount.incrementAndGet();
            countRejected();
            log.error("兜底路由与 Nacos 动态路由合并后校验失败，保留上一版: rejectedCount={}, errors={}",
                    rejected, mergedErrors);
            return degrade(fallback);
        }
        return replaceSnapshot(merged);
    }

    /**
     * Nacos 侧不可用时的降级：已有快照保持不变，首次加载则采用兜底路由
     *
     * @param fallback 兜底路由
     * @return 生效的路由定义
     */
    private List<RouteDefinition> degrade(List<RouteDefinition> fallback) {
        return currentRoutes.isEmpty() ? replaceSnapshot(fallback) : currentRoutes;
    }

    /**
     * 将候选路由替换为当前快照（回滚固定版本与内容等价时保持不动）
     *
     * @param candidate 候选路由定义列表
     * @return 生效的路由定义
     */
    private List<RouteDefinition> replaceSnapshot(List<RouteDefinition> candidate) {
        // 快照读写与回滚共用同一监视器，避免刷新线程与治理端点并发时出现「上一版指向新当前」的中间态
        synchronized (this) {
            if (isAbandoned(candidate)) {
                log.info("Nacos 动态路由仍为回滚前被放弃的版本，保持回滚后快照: dataId={}, routeCount={}",
                        DATA_ID, currentRoutes.size());
                return currentRoutes;
            }
            if (sameAsCurrent(candidate)) {
                log.debug("网关路由配置未变化，保持当前快照: dataId={}, routeCount={}",
                        DATA_ID, currentRoutes.size());
                return currentRoutes;
            }
            previousRoutes = currentRoutes;
            currentRoutes = List.copyOf(candidate);
            abandonedSignatures = null;
            return currentRoutes;
        }
    }

    /**
     * 按路由 id 合并兜底路由与动态路由，动态路由优先覆盖同 id 条目
     *
     * @param fallback 兜底路由
     * @param dynamic  动态路由
     * @return 合并后的路由列表（id 唯一，保持兜底在前、动态新增在后）
     */
    private static List<RouteDefinition> merge(List<RouteDefinition> fallback, List<RouteDefinition> dynamic) {
        Map<String, RouteDefinition> merged = new LinkedHashMap<>();
        for (RouteDefinition route : fallback) {
            merged.put(route.getId(), route);
        }
        for (RouteDefinition route : dynamic) {
            merged.put(route.getId(), route);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 加载兜底路由（进程内缓存，文件为构建期静态资源）
     *
     * @return 兜底路由定义列表；未启用或文件不可用时为空列表
     */
    private List<RouteDefinition> loadFallbackRoutes() {
        if (!fallbackEnabled) {
            return List.of();
        }
        List<RouteDefinition> cached = fallbackRoutes;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (fallbackRoutes == null) {
                fallbackRoutes = readFallbackRoutes();
            }
            return fallbackRoutes;
        }
    }

    /**
     * 读取 classpath 兜底路由文件。
     *
     * <p>与 Nacos 侧共用 {@link #convert(String)} 解析器（{@code whale.gateway.dynamic-routes} 结构），
     * 规避 Spring Binder 将 {@code Path=/x/**} 简写绑定为 {@code _genkey_0} 参数、
     * 与 {@link RouteDefinitionValidator} 只识别 {@code patterns} 的规则冲突。</p>
     *
     * @return 校验通过的兜底路由；读取或校验失败时为空列表（告警但不阻断启动）
     */
    private List<RouteDefinition> readFallbackRoutes() {
        Resource resource = new DefaultResourceLoader().getResource(fallbackLocation);
        if (!resource.exists()) {
            log.warn("网关路由兜底文件不存在，兜底路由为空: location={}", fallbackLocation);
            return List.of();
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            List<RouteDefinition> routes = convert(content);
            if (routes.isEmpty()) {
                log.warn("网关路由兜底文件未解析出路由: location={}", fallbackLocation);
                return List.of();
            }
            List<String> errors = RouteDefinitionValidator.validateAll(routes);
            if (!errors.isEmpty()) {
                log.error("网关路由兜底文件校验失败，忽略兜底路由: location={}, errors={}", fallbackLocation, errors);
                return List.of();
            }
            log.info("网关路由兜底加载完成: location={}, routeCount={}", fallbackLocation, routes.size());
            return List.copyOf(routes);
        } catch (IOException e) {
            log.error("读取网关路由兜底文件失败，忽略兜底路由: location={}, reason={}",
                    fallbackLocation, e.getMessage());
            return List.of();
        }
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
     * <p>参数按键名排序后拼接，消除 YAML 解析得到的 Map 迭代顺序差异，
     * 避免 Nacos 侧仅调整键顺序就被误判为「配置变更」而触发无意义的 RefreshRoutesEvent。</p>
     *
     * @param definition 路由定义
     * @return 比对签名
     */
    private static String signature(RouteDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.getId()).append('|').append(definition.getUri())
                .append('|').append(definition.getOrder());
        for (PredicateDefinition predicate : definition.getPredicates()) {
            builder.append('|').append(predicate.getName()).append(sortedArgs(predicate.getArgs()));
        }
        for (FilterDefinition filter : definition.getFilters()) {
            builder.append('|').append(filter.getName()).append(sortedArgs(filter.getArgs()));
        }
        return builder.toString();
    }

    /**
     * 将参数映射按 key 排序后拼接为稳定字符串
     *
     * @param args 参数映射，可为 {@code null}
     * @return 排序后的稳定字符串表示
     */
    private static String sortedArgs(Map<String, String> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        return new TreeMap<>(args).toString();
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
     * <p>注意回滚后的语义：{@link #rollback()} 会交换 current/previous，
     * 因此回滚成功后本方法返回的是「刚刚被放弃的版本」，再次回滚即可切回；
     * 治理端点若需区分，应结合 {@link #isRolledBack()} 展示。</p>
     *
     * @return 上一版路由定义列表（不可变）
     */
    public List<RouteDefinition> getPreviousRoutes() {
        return previousRoutes;
    }

    /**
     * 判断当前快照是否处于「回滚后固定」状态（即 Nacos 中仍是回滚时被放弃的版本）
     *
     * @return 处于回滚固定状态返回 {@code true}
     */
    public boolean isRolledBack() {
        return abandonedSignatures != null;
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
        Integer order;
        try {
            order = toOrder(map.get(FIELD_ORDER));
        } catch (IllegalArgumentException e) {
            log.warn("动态路由 order 非法，跳过: id={}, order={}", id, map.get(FIELD_ORDER));
            return null;
        }
        if (order != null) {
            definition.setOrder(order);
        }
        return definition;
    }

    /**
     * 解析路由优先级字段。
     *
     * <p>缺省（{@code null}/空白）返回 {@code null}，由网关按默认值 0 处理；
     * 非整数值视为配置错误，整条路由跳过（与非法 uri 一致），避免静默按 0 排序导致路由命中顺序错乱。</p>
     *
     * @param value 原始字段值
     * @return 优先级；缺省返回 {@code null}
     */
    private static Integer toOrder(Object value) {
        String raw = stringValue(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("动态路由 order 必须为整数：" + raw, e);
        }
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
