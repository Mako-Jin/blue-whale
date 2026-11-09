package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.iot.config.IotProperties;
import com.bluewhale.iot.dto.TelemetryIngestResult;
import com.bluewhale.iot.dto.TelemetryPoint;
import com.bluewhale.iot.dto.TelemetryVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotTelemetry;
import com.bluewhale.iot.entity.IotThingModel;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.mapper.IotTelemetryMapper;
import com.bluewhale.iot.mapper.IotThingModelMapper;
import com.bluewhale.iot.support.JsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 遥测服务：上行数据解析、按物模型校验分流、落库、影子更新与实时推送。
 *
 * <p>入口是 EMQX {@code message.publish} Webhook 桥接（{@code /iot/internal/emqx/telemetry}），
 * 不是常驻 MQTT 客户端：Broker 把 {@code up/property|event|reply} 主题的报文转发过来，
 * 本服务解析 topic 定位设备、按物模型 {@code data_type} 分流数值/文本、落 {@code iot_telemetry}
 * 并更新设备影子与最近上报时间。</p>
 *
 * <p><b>脏数据口径</b>：未知标识符、类型不符、超出批次上限一律<b>丢弃并计数</b>，
 * 接口恒返回 200——单条脏数据不能让 Broker 反复重投，也不能阻塞同批其它数据。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotTelemetryService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 上行主题前缀 */
    private static final String UP_PREFIX = "up/";

    /** 上行类别：属性 */
    private static final String KIND_PROPERTY = "property";

    /** 上行类别：事件 */
    private static final String KIND_EVENT = "event";

    /** 上行类别：指令回执 */
    private static final String KIND_REPLY = "reply";

    /** 事件标识符前缀（事件与属性共用一张遥测表，靠前缀区分） */
    private static final String EVENT_IDENTIFIER_PREFIX = "event:";

    /** 数值数据类型：整型 */
    private static final String TYPE_INT = "int";

    /** 数值数据类型：浮点 */
    private static final String TYPE_DOUBLE = "double";

    /** 主题分段数（bw/{tenant}/{productCode}/{deviceName}/up/{kind}） */
    private static final int TOPIC_SEGMENTS = 6;

    private final IotTelemetryMapper telemetryMapper;

    private final IotThingModelMapper thingModelMapper;

    private final IotDeviceMapper deviceMapper;

    private final IotProductMapper productMapper;

    private final IotShadowService shadowService;

    private final IotStreamService streamService;

    private final IotCommandService commandService;

    private final IotProperties properties;

    /**
     * 处理一条上行报文（EMQX message.publish 桥接）。
     *
     * @param username 设备用户名（{productCode}/{deviceName}，可空）
     * @param topic    MQTT 主题
     * @param payload  报文（JSON 字符串）
     * @return 入库结果（受理/丢弃条数）
     */
    public TelemetryIngestResult ingest(String username, String topic, String payload) {
        TopicParts parts = TopicParts.parse(topic);
        ResolvedDevice resolved = resolveDevice(username, parts);
        if (resolved == null) {
            log.warn("遥测丢弃：设备无法定位，username={}, topic={}", username, topic);
            return TelemetryIngestResult.builder().accepted(0).rejected(0).reason("device not found").build();
        }
        Long previousTenant = TenantContext.get();
        TenantContext.set(resolved.device().getTenantId());
        try {
            return switch (parts.kind()) {
                case KIND_PROPERTY -> ingestProperties(resolved, payload);
                case KIND_EVENT -> ingestEvent(resolved, payload);
                case KIND_REPLY -> ingestReply(payload);
                default -> TelemetryIngestResult.builder().accepted(0).rejected(0)
                        .reason("unsupported kind: " + parts.kind()).build();
            };
        } finally {
            TenantContext.set(previousTenant);
        }
    }

    /**
     * 查询设备遥测（设备视角，按时间倒序）。
     *
     * @param deviceId   设备ID
     * @param identifier 属性标识符过滤（可空）
     * @param from       起始时间（ISO-8601，可空）
     * @param to         结束时间（ISO-8601，可空）
     * @param limit      条数上限（可空，钳制到配置上限）
     * @return 遥测列表
     */
    public List<TelemetryVO> query(Long deviceId, String identifier, String from, String to, Integer limit) {
        if (deviceMapper.selectById(deviceId) == null) {
            throw new BizException("设备不存在");
        }
        int max = Math.max(1, properties.getTelemetryQueryMax());
        int size = limit == null || limit <= 0 ? Math.min(100, max) : Math.min(limit, max);
        LambdaQueryWrapper<IotTelemetry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IotTelemetry::getDeviceId, deviceId);
        if (StringUtils.hasText(identifier)) {
            wrapper.eq(IotTelemetry::getIdentifier, identifier);
        }
        LocalDateTime start = parseTime(from);
        LocalDateTime end = parseTime(to);
        if (start != null) {
            wrapper.ge(IotTelemetry::getTs, start);
        }
        if (end != null) {
            wrapper.le(IotTelemetry::getTs, end);
        }
        wrapper.orderByDesc(IotTelemetry::getTs).last("LIMIT " + size);
        List<IotTelemetry> rows = telemetryMapper.selectList(wrapper);
        List<TelemetryVO> result = new ArrayList<>(rows.size());
        for (IotTelemetry row : rows) {
            result.add(TelemetryVO.builder()
                    .identifier(row.getIdentifier())
                    .value(row.getValue())
                    .valueText(row.getValueText())
                    .ts(format(row.getTs()))
                    .build());
        }
        return result;
    }

    /**
     * 处理属性上行：按物模型分流数值/文本，落库后更新影子与最近上报时间。
     *
     * @param resolved 已定位设备
     * @param payload  报文
     * @return 入库结果
     */
    private TelemetryIngestResult ingestProperties(ResolvedDevice resolved, String payload) {
        JsonNode root = JsonCodec.tree(payload);
        if (root == null || !root.isObject()) {
            return TelemetryIngestResult.builder().accepted(0).rejected(0).reason("payload not object").build();
        }
        JsonNode propertiesNode = root.path("properties");
        Map<String, Object> values = new LinkedHashMap<>();
        if (propertiesNode.isObject()) {
            propertiesNode.fields().forEachRemaining(entry ->
                    values.put(entry.getKey(), JsonCodec.toValue(entry.getValue())));
        } else {
            root.fields().forEachRemaining(entry -> {
                if (!"ts".equals(entry.getKey())) {
                    values.put(entry.getKey(), JsonCodec.toValue(entry.getValue()));
                }
            });
        }
        LocalDateTime ts = parseTimestamp(root.path("ts"));
        IotDevice device = resolved.device();
        Map<String, String> propertyTypes = loadPropertyTypes(device.getProductId());
        int maxBatch = Math.max(1, properties.getTelemetryMaxBatch());
        int rejected = 0;
        int accepted = 0;
        List<TelemetryPoint> points = new ArrayList<>(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (accepted + rejected >= maxBatch) {
                rejected++;
                continue;
            }
            TelemetryPoint point = toPoint(entry.getKey(), entry.getValue(), ts, propertyTypes);
            if (point == null) {
                rejected++;
                continue;
            }
            insert(row(device, point));
            points.add(point);
            accepted++;
        }
        if (accepted > 0) {
            Map<String, Object> reported = new LinkedHashMap<>();
            for (TelemetryPoint point : points) {
                reported.put(point.getIdentifier(), point.getValue() != null ? point.getValue() : point.getValueText());
            }
            device.setLastReportTime(LocalDateTime.now());
            shadowService.applyReported(device, reported, ts);
            streamService.publish(device.getId(), IotStreamService.EVENT_TELEMETRY, points);
        }
        return TelemetryIngestResult.builder().accepted(accepted).rejected(rejected).build();
    }

    /**
     * 处理事件上行：事件载荷整体存 {@code value_text}，标识符加 {@code event:} 前缀。
     *
     * @param resolved 已定位设备
     * @param payload  报文
     * @return 入库结果
     */
    private TelemetryIngestResult ingestEvent(ResolvedDevice resolved, String payload) {
        JsonNode root = JsonCodec.tree(payload);
        if (root == null || !root.isObject()) {
            return TelemetryIngestResult.builder().accepted(0).rejected(0).reason("payload not object").build();
        }
        String identifier = root.path("identifier").asText("");
        if (!StringUtils.hasText(identifier)) {
            return TelemetryIngestResult.builder().accepted(0).rejected(1).reason("event identifier missing").build();
        }
        TelemetryPoint point = TelemetryPoint.builder()
                .identifier(EVENT_IDENTIFIER_PREFIX + identifier)
                .valueText(JsonCodec.write(JsonCodec.toValue(root)))
                .ts(parseTimestamp(root.path("ts")))
                .build();
        insert(row(resolved.device(), point));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("identifier", identifier);
        event.put("payload", JsonCodec.toValue(root));
        event.put("ts", format(point.getTs()));
        streamService.publish(resolved.device().getId(), IotStreamService.EVENT_EVENT, event);
        return TelemetryIngestResult.builder().accepted(1).rejected(0).build();
    }

    /**
     * 处理指令回执上行：交给指令服务按 {@code message_id} 收敛状态。
     *
     * @param payload 报文
     * @return 入库结果（回执不落遥测表）
     */
    private TelemetryIngestResult ingestReply(String payload) {
        boolean handled = commandService.onReply(payload);
        return TelemetryIngestResult.builder().accepted(handled ? 1 : 0).rejected(handled ? 0 : 1)
                .reason(handled ? null : "reply not matched").build();
    }

    /**
     * 单个上报值按物模型数据类型转为数据点。
     *
     * @param identifier    标识符
     * @param raw           原始值
     * @param ts            时间戳
     * @param propertyTypes 物模型属性类型表（可空）
     * @return 数据点；应丢弃时返回 {@code null}
     */
    private static TelemetryPoint toPoint(String identifier, Object raw, LocalDateTime ts,
                                          Map<String, String> propertyTypes) {
        if (!StringUtils.hasText(identifier) || raw == null) {
            return null;
        }
        String dataType = propertyTypes.get(identifier);
        if (!propertyTypes.isEmpty() && dataType == null) {
            return null;
        }
        LocalDateTime time = ts == null ? LocalDateTime.now() : ts;
        boolean numericType = TYPE_INT.equals(dataType) || TYPE_DOUBLE.equals(dataType);
        if (raw instanceof Number number && (numericType || dataType == null)) {
            return TelemetryPoint.builder()
                    .identifier(identifier).value(number.doubleValue()).ts(time).build();
        }
        if (numericType) {
            return null;
        }
        String text = raw instanceof String string ? string : JsonCodec.write(raw);
        return TelemetryPoint.builder()
                .identifier(identifier).valueText(text).ts(time).build();
    }

    /**
     * 组装遥测行。
     *
     * @param device 设备
     * @param point  数据点
     * @return 遥测实体
     */
    private static IotTelemetry row(IotDevice device, TelemetryPoint point) {
        IotTelemetry row = new IotTelemetry();
        row.setTenantId(device.getTenantId());
        row.setDeviceId(device.getId());
        row.setIdentifier(point.getIdentifier());
        row.setValue(point.getValue());
        row.setValueText(point.getValueText());
        row.setTs(point.getTs() == null ? LocalDateTime.now() : point.getTs());
        return row;
    }

    /**
     * 落库单条遥测。
     *
     * @param row 遥测实体
     */
    private void insert(IotTelemetry row) {
        telemetryMapper.insert(row);
    }

    /**
     * 载入产品属性类型表（标识符 → 数据类型）。
     *
     * @param productId 产品ID
     * @return 类型表；产品无属性定义时返回空表
     */
    private Map<String, String> loadPropertyTypes(Long productId) {
        List<IotThingModel> models = thingModelMapper.selectList(new LambdaQueryWrapper<IotThingModel>()
                .eq(IotThingModel::getProductId, productId)
                .eq(IotThingModel::getKind, IotThingModelService.KIND_PROPERTY));
        Map<String, String> types = new HashMap<>(models.size());
        for (IotThingModel model : models) {
            types.put(model.getIdentifier(), model.getDataType());
        }
        return types;
    }

    /**
     * 定位设备：优先用 Webhook 的 username（认证时已确定身份），回落主题分段。
     *
     * @param username 用户名
     * @param parts    主题解析结果
     * @return 设备与产品编码；无法定位返回 {@code null}
     */
    private ResolvedDevice resolveDevice(String username, TopicParts parts) {
        String productCode;
        String deviceName;
        int idx = StringUtils.hasText(username) ? username.indexOf('/') : -1;
        if (idx > 0 && idx < username.length() - 1) {
            productCode = username.substring(0, idx);
            deviceName = username.substring(idx + 1);
        } else if (parts.productCode() != null && parts.deviceName() != null) {
            productCode = parts.productCode();
            deviceName = parts.deviceName();
        } else {
            return null;
        }
        IotProduct product = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getCode, productCode)
                .last("LIMIT 1"));
        if (product == null) {
            return null;
        }
        IotDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getProductId, product.getId())
                .eq(IotDevice::getDeviceName, deviceName)
                .last("LIMIT 1"));
        if (device == null) {
            return null;
        }
        return new ResolvedDevice(device, product.getCode());
    }

    /**
     * 解析时间戳字段（epoch 毫秒或 ISO-8601）。
     *
     * @param node JSON 节点
     * @return 时间；缺省返回 {@code null}
     */
    private static LocalDateTime parseTimestamp(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.systemDefault());
        }
        return parseTime(node.asText());
    }

    /**
     * 解析时间字符串。
     *
     * @param text ISO-8601 文本
     * @return 时间；空或非法返回 {@code null}
     */
    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, TIME_FORMAT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 文本；空值返回 {@code null}
     */
    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    /**
     * 已定位的设备（设备实体 + 产品编码）。
     *
     * @param device      设备实体
     * @param productCode 产品编码
     */
    private record ResolvedDevice(IotDevice device, String productCode) {
    }

    /**
     * 上行主题解析结果（{@code bw/{tenant}/{productCode}/{deviceName}/up/{kind}}）。
     *
     * @param tenant      租户分段（仅参考，真实归属以设备表为准）
     * @param productCode 产品编码
     * @param deviceName  设备名
     * @param kind        上行类别：property/event/reply
     */
    private record TopicParts(String tenant, String productCode, String deviceName, String kind) {

        /**
         * 解析主题。
         *
         * @param topic MQTT 主题
         * @return 解析结果；结构不符时各字段为空且 kind 为 {@code unknown}
         */
        static TopicParts parse(String topic) {
            if (topic == null) {
                return new TopicParts(null, null, null, "unknown");
            }
            String[] segments = topic.split("/");
            if (segments.length != TOPIC_SEGMENTS || !"up".equals(segments[4])) {
                return new TopicParts(null, null, null, "unknown");
            }
            String kind = segments[5];
            Set<String> supported = Set.of(KIND_PROPERTY, KIND_EVENT, KIND_REPLY);
            return new TopicParts(segments[1], segments[2], segments[3],
                    supported.contains(kind) ? kind : "unknown");
        }
    }
}
