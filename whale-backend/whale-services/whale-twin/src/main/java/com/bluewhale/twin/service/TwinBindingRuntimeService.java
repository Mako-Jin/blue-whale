package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.dto.BindingStateVO;
import com.bluewhale.twin.entity.TwinBinding;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.support.IotStateClient;
import com.bluewhale.twin.support.InternalApiStateClient;
import com.bluewhale.twin.support.TwinAlertTracker;
import com.bluewhale.twin.support.TwinThresholdEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 绑定实时态运行时：轮询 whale-iot 内网接口 → 属性映射 → 差分 → 场景 SSE 推送。
 *
 * <p>调度模型：全局 tick（{@code whale.twin.runtime.tick-ms}），分两条通道：</p>
 * <ul>
 *   <li><b>前台通道</b>：只处理有活跃 SSE 订阅者的场景（有人看，差分即时推送）；</li>
 *   <li><b>无头通道</b>（Wave 6.08 M0）：每 {@code headless-interval-ms} 扫一遍
 *       「有启用绑定的场景」，跳过本 tick 已在前台通道刷过的场景，让告警评估与
 *       落库不依赖是否有人打开 3D 查看器。</li>
 * </ul>
 *
 * <p>场景内逐绑定判断 {@code pollIntervalMs} 是否到期，未到期的沿用上次快照；
 * 同一 tick 内同一设备只拉一次实时态（多绑定共享），internal_api 源同理按
 * bindKey 去重。</p>
 *
 * <p>差分口径：绑定粒度的 {@code online + values} 序列化串与上次不一致才算变化；
 * 首次tick视为全量变化（订阅端需要初始态）。源类型支持 {@code iot_device}
 * （设备影子）与 {@code internal_api}（内部只读接口，propertyMap 键为
 * data 载荷内的 JSON 点路径）。差分推送之外，按 {@code propertyMap.thresholds}
 * 逐键评估告警级别（6.07 起叠加持续确认与回差防抖），生效级别变化才推
 * {@code alert} 帧（见 {@link #publishAlerts}）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class TwinBindingRuntimeService {

    /** 默认轮询间隔下限（毫秒） */
    private static final int MIN_POLL_INTERVAL_MS = 1000;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** JSON 解析器（读 propertyMap 与序列化差分指纹） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TwinBindingService bindingService;

    private final TwinEntityMapper entityMapper;

    private final IotStateClient iotStateClient;

    private final InternalApiStateClient internalApiStateClient;

    private final TwinStreamService streamService;

    private final TwinAlertService alertService;

    /** 告警外发（Wave 6.10 M0：落库后异步推 webhook） */
    private final TwinAlertNotifyService alertNotifyService;

    private final TwinProperties properties;

    /** 场景ID → (绑定ID → 上次快照指纹)：不一致才推送 */
    private final Map<Long, Map<Long, String>> lastSnapshots = new ConcurrentHashMap<>();

    /** 绑定ID → 下次允许轮询的 epoch 毫秒（包内可见便于单测清空重放） */
    final Map<Long, Long> nextDueAt = new ConcurrentHashMap<>();

    /** 场景ID → 告警级别跟踪器（持续确认 + 回差，6.07 M0） */
    private final Map<Long, TwinAlertTracker> alertTrackers = new ConcurrentHashMap<>();

    /** 轮询调度线程池 */
    private final ScheduledExecutorService scheduler;

    /** 上次无头评估时刻（epoch 毫秒；包内可见便于单测重置节流窗口） */
    volatile long lastHeadlessAt;

    /**
     * 构造运行时并按配置启动轮询调度。
     *
     * @param bindingService         绑定服务
     * @param entityMapper           实体 Mapper
     * @param iotStateClient         whale-iot 客户端
     * @param internalApiStateClient internal_api 绑定源客户端
     * @param streamService          场景推送服务
     * @param alertService           阈值告警服务（落库）
     * @param alertNotifyService     告警外发服务（webhook，默认关闭）
     * @param properties             孪生配置
     */
    public TwinBindingRuntimeService(TwinBindingService bindingService,
                                     TwinEntityMapper entityMapper,
                                     IotStateClient iotStateClient,
                                     InternalApiStateClient internalApiStateClient,
                                     TwinStreamService streamService,
                                     TwinAlertService alertService,
                                     TwinAlertNotifyService alertNotifyService,
                                     TwinProperties properties) {
        this.bindingService = bindingService;
        this.entityMapper = entityMapper;
        this.iotStateClient = iotStateClient;
        this.internalApiStateClient = internalApiStateClient;
        this.streamService = streamService;
        this.alertService = alertService;
        this.alertNotifyService = alertNotifyService;
        this.properties = properties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "twin-binding-runtime");
            thread.setDaemon(true);
            return thread;
        });
        if (properties.getRuntime().isEnabled()) {
            long tick = Math.max(1000L, properties.getRuntime().getTickMs());
            scheduler.scheduleAtFixedRate(this::tick, tick, tick, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 当前绑定实时态快照（订阅端初始化用；不做差分，全量返回到期绑定）。
     *
     * @param sceneId 场景ID
     * @return 各绑定实时态列表
     */
    public List<BindingStateVO> currentStates(Long sceneId) {
        List<TwinBinding> bindings = bindingService.listByScene(sceneId);
        return computeStates(sceneId, bindings, new ConcurrentHashMap<>());
    }

    /**
     * 单次调度 tick：先刷有订阅者的场景（前台通道），再按节流周期做无头评估。
     */
    void tick() {
        Set<Long> active;
        try {
            active = streamService.activeScenes();
        } catch (RuntimeException e) {
            // 调度线程里异常必须吞掉，否则后续 tick 全部停摆
            log.warn("绑定轮询 tick 异常（忽略）：{}", e.getMessage());
            return;
        }
        try {
            for (Long sceneId : active) {
                refreshScene(sceneId, false);
            }
            tickHeadless(active);
        } catch (RuntimeException e) {
            log.warn("绑定轮询 tick 异常（忽略）：{}", e.getMessage());
        }
    }

    /**
     * 无头评估通道（Wave 6.08 M0）：无人订阅的场景也周期评估并按级别变化落库告警。
     *
     * <p>与前台通道的分工：本 tick 已有订阅者的场景直接跳过（前台刚拉过，
     * 再拉一次等于翻倍调用数据源）；绑定级 {@code pollIntervalMs} 节流照旧生效。
     * 跑完按「前台场景 ∪ 本轮无头场景」收窄差分指纹与告警状态，避免长跑内存无界。</p>
     *
     * @param active 本 tick 的活跃订阅场景
     */
    private void tickHeadless(Set<Long> active) {
        TwinProperties.Runtime runtime = properties.getRuntime();
        if (!runtime.isHeadlessEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHeadlessAt < Math.max(1000L, runtime.getHeadlessIntervalMs())) {
            return;
        }
        lastHeadlessAt = now;
        List<Long> candidates = bindingService.listScenesWithEnabledBindings(runtime.getMaxHeadlessScenes());
        Set<Long> keep = new HashSet<>(active);
        for (Long sceneId : candidates) {
            if (active.contains(sceneId)) {
                continue;
            }
            keep.add(sceneId);
            refreshScene(sceneId, true);
        }
        lastSnapshots.keySet().retainAll(keep);
        alertTrackers.keySet().retainAll(keep);
    }

    /**
     * 刷新单场景：取到期绑定 → 拉实时态 → 差分 → 推送变化批次 → 评估告警。
     *
     * @param sceneId  场景ID
     * @param headless 是否由无头通道触发（仅影响日志口径）
     */
    private void refreshScene(Long sceneId, boolean headless) {
        List<TwinBinding> bindings = bindingService.listByScene(sceneId);
        if (bindings.isEmpty()) {
            return;
        }
        Map<Long, String> lastScene = lastSnapshots.computeIfAbsent(sceneId, key -> new ConcurrentHashMap<>());
        Map<Long, BindingStateVO> computed = new LinkedHashMap<>();
        for (BindingStateVO state : computeStates(sceneId, bindings, nextDueAt)) {
            computed.put(Long.valueOf(state.getBindingId()), state);
        }
        List<BindingStateVO> changed = new ArrayList<>();
        for (TwinBinding binding : bindings) {
            if (!TwinBindingService.SOURCE_IOT_DEVICE.equals(binding.getSourceType())
                    && !TwinBindingService.SOURCE_INTERNAL_API.equals(binding.getSourceType())) {
                continue;
            }
            BindingStateVO state = computed.get(binding.getId());
            if (state == null) {
                continue;
            }
            String fingerprint = fingerprint(state);
            String previous = lastScene.get(binding.getId());
            if (!fingerprint.equals(previous)) {
                changed.add(state);
                lastScene.put(binding.getId(), fingerprint);
            }
        }
        if (!changed.isEmpty()) {
            streamService.publish(sceneId, TwinStreamService.EVENT_BINDING, Map.of(
                    "sceneId", sceneId.toString(),
                    "ts", TIME_FORMAT.format(LocalDateTime.now()),
                    "items", changed));
        }
        publishAlerts(sceneId, bindings, computed.values(), headless);
    }

    /**
     * 阈值告警评估与推送：对本 tick 拉到实时态的绑定逐键分级，交给
     * {@link TwinAlertTracker} 做持续确认与回差修正，生效级别有变化
     * （含首评越界、升级、降级、恢复）才推 {@code alert} 帧；
     * 离线绑定静默清级（上下线由 binding 帧表达，恢复在线后重新首评）。
     *
     * @param sceneId 场景ID
     * @param bindings 绑定列表（取 propertyMap 与清理已删绑定）
     * @param states   本 tick 计算出的实时态
     * @param headless 是否由无头通道触发（无人订阅时补一条 INFO 留痕日志）
     */
    private void publishAlerts(Long sceneId, List<TwinBinding> bindings,
                               Iterable<BindingStateVO> states, boolean headless) {
        Map<Long, TwinBinding> bindingById = new HashMap<>(bindings.size() * 2);
        for (TwinBinding binding : bindings) {
            bindingById.put(binding.getId(), binding);
        }
        TwinAlertTracker tracker =
                alertTrackers.computeIfAbsent(sceneId, key -> new TwinAlertTracker());
        // 已删除的绑定不再占用级别表
        tracker.retain(bindingById.keySet());
        long now = System.currentTimeMillis();
        String ts = TIME_FORMAT.format(LocalDateTime.now());
        List<BindingAlertVO> alerts = new ArrayList<>();
        for (BindingStateVO state : states) {
            Long bindingId = Long.valueOf(state.getBindingId());
            if (!Boolean.TRUE.equals(state.getOnline())) {
                tracker.reset(bindingId);
                continue;
            }
            TwinBinding binding = bindingById.get(bindingId);
            if (binding == null) {
                continue;
            }
            Map<String, TwinThresholdEvaluator.Level> evaluated =
                    TwinThresholdEvaluator.evaluate(binding.getPropertyMap(), state.getValues());
            if (evaluated.isEmpty()) {
                continue;
            }
            for (TwinAlertTracker.Transition change
                    : tracker.observe(bindingId, evaluated.values(), now)) {
                alerts.add(BindingAlertVO.builder()
                        .bindingId(state.getBindingId())
                        .entityId(state.getEntityId())
                        .entityCode(state.getEntityCode())
                        .stateKey(change.stateKey())
                        .value(change.value())
                        .previous(change.previous())
                        .level(change.level())
                        .thresholds(change.thresholds())
                        .ts(ts)
                        .build());
            }
        }
        if (!alerts.isEmpty()) {
            streamService.publish(sceneId, TwinStreamService.EVENT_ALERT, Map.of(
                    "sceneId", sceneId.toString(),
                    "ts", ts,
                    "items", alerts));
            if (headless) {
                // 无人订阅场景：SSE 空转，靠日志留痕（同帧内容，便于事后对账）
                for (BindingAlertVO alert : alerts) {
                    log.info("无头告警：sceneId={}, bindingId={}, stateKey={}, level={}, previous={}, value={}",
                            sceneId, alert.getBindingId(), alert.getStateKey(),
                            alert.getLevel(), alert.getPrevious(), alert.getValue());
                }
            }
            // M1：与 SSE 推送同源落库（SSE 帧已发出，落库失败不阻塞主链路）
            for (BindingAlertVO alert : alerts) {
                TwinBinding binding = bindingById.get(Long.valueOf(alert.getBindingId()));
                Long tenantId = binding == null ? null : binding.getTenantId();
                Long alertId = alertService.record(sceneId, tenantId, alert);
                // Wave 6.10：落库后外发（webhook / 站内信；异步，不影响 tick）
                alertNotifyService.notify(sceneId, tenantId, alertId, alert);
            }
        }
    }

    /**
     * 计算绑定实时态（内部共用：快照接口传空 dueMap 表示忽略到期）。
     *
     * @param sceneId 场景ID
     * @param bindings 绑定列表
     * @param dueMap   绑定到期表（可空；为 null 时忽略到期判断）
     * @return 实时态列表
     */
    private List<BindingStateVO> computeStates(Long sceneId,
                                               List<TwinBinding> bindings,
                                               Map<Long, Long> dueMap) {
        long now = System.currentTimeMillis();
        Map<Long, TwinEntity> entityById = new HashMap<>(bindings.size() * 2);
        // 设备级缓存：同 tick 内同设备只拉一次
        Map<String, Map<String, Object>> deviceCache = new HashMap<>();
        // 接口级缓存：同 tick 内同 bindKey 只拉一次（白名单校验在客户端内做）
        Map<String, Map<String, Object>> apiCache = new HashMap<>();
        List<BindingStateVO> result = new ArrayList<>();
        int devices = 0;
        int apis = 0;
        for (TwinBinding binding : bindings) {
            boolean iotSource = TwinBindingService.SOURCE_IOT_DEVICE.equals(binding.getSourceType());
            boolean apiSource = TwinBindingService.SOURCE_INTERNAL_API.equals(binding.getSourceType());
            if (!iotSource && !apiSource) {
                continue;
            }
            if (binding.getEnabled() != null && binding.getEnabled() != 1) {
                continue;
            }
            if (dueMap != null && !isDue(binding, dueMap, now)) {
                continue;
            }
            TwinEntity entity = entityById.computeIfAbsent(binding.getEntityId(),
                    key -> entityMapper.selectById(key));
            BindingStateVO state = iotSource
                    ? buildState(binding, entity, binding.getBindKey(), deviceCache, sceneId)
                    : buildApiState(binding, entity, apiCache, sceneId);
            if (state != null) {
                result.add(state);
                if (iotSource) {
                    devices++;
                } else {
                    apis++;
                }
            }
            int cap = iotSource
                    ? properties.getRuntime().getMaxDevicesPerScene()
                    : properties.getRuntime().getMaxApisPerScene();
            int hit = iotSource ? devices : apis;
            if (hit >= cap) {
                log.warn("场景绑定源数达到上限，本 tick 截断：sceneId={}, sourceType={}, max={}",
                        sceneId, binding.getSourceType(), cap);
                break;
            }
        }
        return result;
    }

    /**
     * 组装单个绑定实时态（设备态走缓存；拉取失败视为离线）。
     *
     * @param binding     绑定实体
     * @param entity      实体实体（可空）
     * @param deviceIdKey 设备ID
     * @param cache       设备态缓存
     * @param sceneId     场景ID
     * @return 实时态视图（绑定实体已删时返回 null）
     */
    private BindingStateVO buildState(TwinBinding binding, TwinEntity entity, String deviceIdKey,
                                      Map<String, Map<String, Object>> cache, Long sceneId) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> deviceState = cache.computeIfAbsent(deviceIdKey,
                key -> iotStateClient.fetchDeviceState(Long.valueOf(key), entity.getTenantId()));
        boolean online = extractOnline(deviceState);
        Map<String, Object> reported = extractReported(deviceState);
        return BindingStateVO.builder()
                .bindingId(String.valueOf(binding.getId()))
                .entityId(String.valueOf(binding.getEntityId()))
                .entityCode(entity.getEntityCode())
                .sourceType(binding.getSourceType())
                .bindKey(binding.getBindKey())
                .online(online)
                .values(applyPropertyMap(binding.getPropertyMap(), reported, online))
                .ts(TIME_FORMAT.format(LocalDateTime.now()))
                .build();
    }

    /**
     * 组装 internal_api 绑定实时态（同 tick 内同 bindKey 走缓存；
     * 拉取失败视为离线，values 留空）。
     *
     * @param binding 绑定实体
     * @param entity  实体实体（可空）
     * @param cache   接口数据缓存（bindKey → data 载荷）
     * @param sceneId 场景ID
     * @return 实时态视图（绑定实体已删时返回 null）
     */
    private BindingStateVO buildApiState(TwinBinding binding, TwinEntity entity,
                                         Map<String, Map<String, Object>> cache, Long sceneId) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> data = cache.computeIfAbsent(binding.getBindKey(),
                key -> internalApiStateClient.fetch(key, entity.getTenantId()));
        boolean online = data != null;
        return BindingStateVO.builder()
                .bindingId(String.valueOf(binding.getId()))
                .entityId(String.valueOf(binding.getEntityId()))
                .entityCode(entity.getEntityCode())
                .sourceType(binding.getSourceType())
                .bindKey(binding.getBindKey())
                .online(online)
                .values(applyApiPropertyMap(binding.getPropertyMap(), data))
                .ts(TIME_FORMAT.format(LocalDateTime.now()))
                .build();
    }

    /**
     * internal_api 属性映射：键为 data 载荷内的 JSON 点路径（支持数组下标，
     * 如 {@code temperature} / {@code list[0].value}），值语义与设备源一致
     * （字符串=状态键；对象取 {@code stateKey}，thresholds 留给前端解释）。
     *
     * @param propertyMap 属性映射 JSON
     * @param data        信封 data 载荷（可空）
     * @return 状态键 → 取值
     */
    private Map<String, Object> applyApiPropertyMap(String propertyMap, Map<String, Object> data) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (data == null || data.isEmpty() || !StringUtils.hasText(propertyMap)) {
            return values;
        }
        JsonNode payload = JSON.valueToTree(data);
        try {
            JsonNode mapping = JSON.readTree(propertyMap);
            if (!mapping.isObject()) {
                return values;
            }
            var fields = mapping.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode value = resolveJsonPath(payload, entry.getKey());
                if (value == null || value.isMissingNode() || value.isNull()) {
                    continue;
                }
                String stateKey = entry.getKey();
                JsonNode target = entry.getValue();
                if (target.isTextual()) {
                    stateKey = target.asText(stateKey);
                } else if (target.isObject() && target.hasNonNull("stateKey")) {
                    stateKey = target.get("stateKey").asText(stateKey);
                }
                values.put(stateKey, JSON.convertValue(value, Object.class));
            }
        } catch (Exception e) {
            log.warn("internal_api 属性映射解析失败，本条按空值处理：key={}, reason={}",
                    bindingPropertyMapKey(propertyMap), e.getMessage());
        }
        return values;
    }

    /**
     * 按 JSON 点路径取节点：段与段之间按 {@code .} 分隔，段内支持
     * {@code [idx]} 数组下标（如 {@code rows[0].total}）。
     *
     * @param root 根节点
     * @param path 点路径
     * @return 命中节点；路径不存在返回 {@code null}
     */
    private JsonNode resolveJsonPath(JsonNode root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            String field = segment;
            int bracket = segment.indexOf('[');
            if (bracket >= 0) {
                field = segment.substring(0, bracket);
            }
            if (StringUtils.hasText(field)) {
                current = current.get(field);
                if (current == null) {
                    return null;
                }
            }
            while (bracket >= 0) {
                int close = segment.indexOf(']', bracket);
                if (close < 0) {
                    return null;
                }
                int index;
                try {
                    index = Integer.parseInt(segment.substring(bracket + 1, close).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
                current = current.get(index);
                if (current == null) {
                    return null;
                }
                bracket = segment.indexOf('[', close);
            }
        }
        return current;
    }

    /**
     * 判断绑定本轮是否到期（首次必发；之后按 pollIntervalMs 节流）。
     *
     * @param binding 绑定实体
     * @param dueMap  到期表
     * @param now     当前毫秒
     * @return true 到期
     */
    private boolean isDue(TwinBinding binding, Map<Long, Long> dueMap, long now) {
        Long due = dueMap.get(binding.getId());
        if (due == null || now >= due) {
            int interval = binding.getPollIntervalMs() == null
                    ? MIN_POLL_INTERVAL_MS : binding.getPollIntervalMs();
            dueMap.put(binding.getId(), now + Math.max(MIN_POLL_INTERVAL_MS, interval));
            return true;
        }
        return false;
    }

    /**
     * 按属性映射抽值：源字段取设备 reported 值，落到实体状态键。
     *
     * <p>映射值为字符串时直接换键；为对象时取 {@code stateKey}（缺省沿用源字段名），
     * {@code thresholds} 留给前端解释，后端不参与配色。</p>
     *
     * @param propertyMap 属性映射 JSON
     * @param reported    设备上报快照
     * @param online      设备是否在线
     * @return 状态键 → 取值
     */
    private Map<String, Object> applyPropertyMap(String propertyMap,
                                                 Map<String, Object> reported,
                                                 boolean online) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!online || reported.isEmpty()) {
            return values;
        }
        if (!StringUtils.hasText(propertyMap)) {
            return values;
        }
        try {
            JsonNode mapping = JSON.readTree(propertyMap);
            if (!mapping.isObject()) {
                return values;
            }
            var fields = mapping.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String sourceField = entry.getKey();
                if (!reported.containsKey(sourceField)) {
                    continue;
                }
                String stateKey = sourceField;
                JsonNode target = entry.getValue();
                if (target.isTextual()) {
                    stateKey = target.asText(sourceField);
                } else if (target.isObject() && target.hasNonNull("stateKey")) {
                    stateKey = target.get("stateKey").asText(sourceField);
                }
                values.put(stateKey, reported.get(sourceField));
            }
        } catch (Exception e) {
            log.warn("属性映射解析失败，本条按空值处理：binding={}, reason={}",
                    bindingPropertyMapKey(propertyMap), e.getMessage());
        }
        return values;
    }

    /**
     * 差分指纹：online + values 稳定序列化。
     *
     * @param state 实时态
     * @return 指纹串
     */
    private String fingerprint(BindingStateVO state) {
        try {
            return JSON.writeValueAsString(Map.of(
                    "online", Boolean.TRUE.equals(state.getOnline()),
                    "values", state.getValues() == null ? Map.of() : state.getValues()));
        } catch (Exception e) {
            return String.valueOf(state.hashCode());
        }
    }

    /**
     * 从设备态里抽 online（兼容 Boolean 与数值两种序列化）。
     *
     * @param deviceState 设备态
     * @return true 在线
     */
    private boolean extractOnline(Map<String, Object> deviceState) {
        if (deviceState == null) {
            return false;
        }
        Object online = deviceState.get("online");
        if (online instanceof Boolean bool) {
            return bool;
        }
        if (online instanceof Number number) {
            return number.intValue() == 1;
        }
        return false;
    }

    /**
     * 从设备态里抽 reported 快照。
     *
     * @param deviceState 设备态
     * @return reported 映射（缺失返回空映射）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractReported(Map<String, Object> deviceState) {
        if (deviceState == null) {
            return Map.of();
        }
        Object reported = deviceState.get("reported");
        return reported instanceof Map ? (Map<String, Object>) reported : Map.of();
    }

    /**
     * 日志用映射摘要（避免打印全量 JSON）。
     *
     * @param propertyMap 属性映射
     * @return 截断后的摘要
     */
    private String bindingPropertyMapKey(String propertyMap) {
        return propertyMap.length() <= 32 ? propertyMap : propertyMap.substring(0, 32) + "...";
    }

    /**
     * 停服时关掉轮询线程池。
     */
    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
