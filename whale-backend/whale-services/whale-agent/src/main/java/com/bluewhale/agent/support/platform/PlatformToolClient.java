package com.bluewhale.agent.support.platform;

import com.bluewhale.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台只读工具客户端：内网读 whale-iot 实时态/遥测历史与 whale-twin 场景概要。
 *
 * <p>Wave 6.04 三平台联动的执行端——工具契约在 whale-ai 注册表（L1、
 * {@code sys:ai:data:read}），本类经内网令牌调服务 internal 只读接口并解包
 * {@code Result.data}；任何失败抛 {@link PlatformToolException} 由编排层降级。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformToolClient {

    /** JSON 解析器（仅解包 Result 信封） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 查设备实时态（影子 + 在线状态）。
     *
     * @param deviceId    设备ID（可空）
     * @param productCode 产品编码（可空）
     * @param deviceName  设备名（可空）
     * @return 影子 JSON（online/reported/desired/...）
     */
    public JsonNode iotDeviceState(Long deviceId, String productCode, String deviceName) {
        Map<String, String> params = new LinkedHashMap<>();
        if (deviceId != null) {
            params.put("deviceId", String.valueOf(deviceId));
        }
        if (StringUtils.hasText(productCode)) {
            params.put("productCode", productCode);
        }
        if (StringUtils.hasText(deviceName)) {
            params.put("deviceName", deviceName);
        }
        return invoke(properties.getPlatform().getIotBaseUrl(),
                "/iot/internal/device/state", params, "iot-device-state");
    }

    /**
     * 查设备遥测历史（时间倒序）。
     *
     * @param deviceId    设备ID（可空）
     * @param productCode 产品编码（可空）
     * @param deviceName  设备名（可空）
     * @param identifier  属性标识符（可空）
     * @param from        起始时间（可空）
     * @param to          结束时间（可空）
     * @param limit       条数上限（可空）
     * @return 遥测数组 JSON
     */
    public JsonNode iotDeviceHistory(Long deviceId, String productCode, String deviceName,
                                     String identifier, String from, String to, Integer limit) {
        Map<String, String> params = new LinkedHashMap<>();
        if (deviceId != null) {
            params.put("deviceId", String.valueOf(deviceId));
        }
        if (StringUtils.hasText(productCode)) {
            params.put("productCode", productCode);
        }
        if (StringUtils.hasText(deviceName)) {
            params.put("deviceName", deviceName);
        }
        if (StringUtils.hasText(identifier)) {
            params.put("identifier", identifier);
        }
        if (StringUtils.hasText(from)) {
            params.put("from", from);
        }
        if (StringUtils.hasText(to)) {
            params.put("to", to);
        }
        if (limit != null) {
            params.put("limit", String.valueOf(limit));
        }
        return invoke(properties.getPlatform().getIotBaseUrl(),
                "/iot/internal/device/telemetry/history", params, "iot-device-history");
    }

    /**
     * 查孪生场景概要（场景 + 实体树 + 绑定 + 标注）。
     *
     * @param sceneId   场景ID（可空）
     * @param sceneCode 场景编码（可空）
     * @return 概要 JSON（scene/entities/bindings/markers）
     */
    public JsonNode twinSceneOverview(Long sceneId, String sceneCode) {
        Map<String, String> params = new LinkedHashMap<>();
        if (sceneId != null) {
            params.put("sceneId", String.valueOf(sceneId));
        }
        if (StringUtils.hasText(sceneCode)) {
            params.put("sceneCode", sceneCode);
        }
        return invoke(properties.getPlatform().getTwinBaseUrl(),
                "/twin/internal/scene/overview", params, "twin-scene-overview");
    }

    /**
     * 发起内网 GET 并解包 {@code Result.data}。
     *
     * @param baseUrl  服务基地址
     * @param path     接口路径
     * @param params   查询参数（可空）
     * @param toolName 工具名（日志/异常定位用）
     * @return data 节点
     */
    private JsonNode invoke(String baseUrl, String path, Map<String, String> params, String toolName) {
        String query = params.entrySet().stream()
                .map(entry -> urlencode(entry.getKey()) + "=" + urlencode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        String url = baseUrl + path + (query.isEmpty() ? "" : "?" + query);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getPlatform().getTimeoutSeconds())))
                .header("Accept", "application/json")
                .GET();
        String token = properties.getPlatform().getInternalToken();
        if (StringUtils.hasText(token)) {
            builder.header("X-Internal-Token", token);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PlatformToolException(
                        toolName + " 调用失败（HTTP " + response.statusCode() + "）");
            }
            JsonNode root = MAPPER.readTree(response.body());
            if (root.path("code").asInt(-1) != 0) {
                throw new PlatformToolException(
                        toolName + " 业务失败：" + root.path("message").asText("未知错误"));
            }
            return root.path("data");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("平台工具 {} 调用失败：{}", toolName, e.getMessage());
            throw new PlatformToolException(toolName + " 服务不可达：" + e.getMessage(), e);
        }
    }

    /**
     * 查询串转义。
     *
     * @param value 原始值
     * @return 编码值
     */
    private static String urlencode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 平台工具调用失败。
     */
    public static class PlatformToolException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * 构造异常。
         *
         * @param message 消息
         */
        public PlatformToolException(String message) {
            super(message);
        }

        /**
         * 构造异常（带原因）。
         *
         * @param message 消息
         * @param cause   原因
         */
        public PlatformToolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
