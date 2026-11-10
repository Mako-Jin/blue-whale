package com.bluewhale.agent.support.ops;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.common.web.WebHeaders;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 Java 11 {@link HttpClient} 的写操作执行器。
 *
 * <p>仅透传调用方身份头，不携带服务密钥；下游非 2xx 或业务码非 0 一律抛
 * {@link IOException}，由服务层回落为 {@code FAILED} 状态。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class HttpOperationInvoker implements OperationInvoker {

    /** JSON 序列化器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 响应体类型 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Map<String, Object> invoke(String url, Map<String, Object> body, Long userId, String permissionCodes)
            throws IOException, InterruptedException {
        int timeoutSeconds = Math.max(1, properties.getOps().getTimeoutSeconds());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        if (userId != null) {
            builder.header(WebHeaders.USER_ID, String.valueOf(userId));
        }
        if (StringUtils.hasText(permissionCodes)) {
            builder.header(WebHeaders.PERMISSION_CODES, permissionCodes);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("目标服务返回 HTTP " + response.statusCode());
        }
        JsonNode root = MAPPER.readTree(response.body());
        int code = root.path("code").asInt(0);
        if (code != 0) {
            throw new IOException(root.path("message").asText("目标服务执行失败"));
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return new LinkedHashMap<>();
        }
        return MAPPER.convertValue(data, MAP_TYPE);
    }
}
