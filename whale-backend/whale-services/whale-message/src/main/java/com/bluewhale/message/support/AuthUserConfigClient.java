package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.message.config.MessagePreferenceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * auth 用户配置内网客户端：批量读取 {@code notify.<bizType>} 配置值。
 *
 * <p>调用 {@code POST /user/internal/config/batch-get}（网关 403、auth permitAll、
 * X-Internal-Token），带 {@code X-Tenant-Id} 租户上下文。任何失败折算成
 * 「无配置」并记 WARN——偏好是旁路，绝不能阻塞投递主链路（fail-open）。
 * 配置键由调用方拼装（前缀 notify 加业务类型）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AuthUserConfigClient {

    /** auth 内网批量读取路径 */
    private static final String BATCH_GET_PATH = "/user/internal/config/batch-get";

    private final MessagePreferenceProperties properties;

    private final ObjectMapper objectMapper;

    /** 测试注入的客户端；为空时按内网令牌现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：客户端按 {@link InternalTokenSupport} 当前配置现场创建。
     *
     * @param properties   偏好解析配置
     * @param objectMapper JSON 映射器
     */
    @Autowired
    public AuthUserConfigClient(MessagePreferenceProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    /**
     * 测试用构造：外部传入 RestClient。
     *
     * @param properties   偏好解析配置
     * @param objectMapper JSON 映射器
     * @param client       调用客户端（为空表示按内网令牌现场创建）
     */
    AuthUserConfigClient(MessagePreferenceProperties properties, ObjectMapper objectMapper,
                         RestClient client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    /**
     * 批量读取多个用户的指定配置值。
     *
     * @param tenantId 租户 ID（必须）
     * @param key      配置键（notify 前缀 + 业务类型）
     * @param userIds  用户 ID 列表
     * @return {userId: 配置值 JSON}；仅含命中项，失败返回空映射（fail-open）
     */
    public Map<Long, String> batchGet(Long tenantId, String key, List<Long> userIds) {
        if (tenantId == null || !StringUtils.hasText(key) || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId);
        body.put("userIds", userIds);
        body.put("keys", List.of(key));
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            String raw = target.post()
                    .uri(properties.getAuthBaseUrl() + BATCH_GET_PATH)
                    .headers(headers -> {
                        headers.set(InternalTokenSupport.headerName(), InternalTokenSupport.token());
                        headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseItems(raw, userIds);
        } catch (RuntimeException error) {
            log.warn("用户配置批量读取失败（按无配置处理）：key={}, reason={}", key, error.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析 auth 响应信封：code 非 0 或结构不符返回空映射。
     *
     * @param raw     原始响应体
     * @param userIds 请求的用户 ID（过滤结果）
     * @return {userId: 配置值}
     */
    private Map<Long, String> parseItems(String raw, List<Long> userIds) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode code = root.path("code");
            if (!code.isMissingNode() && code.asInt(-1) != 0) {
                log.warn("用户配置批量读取业务失败：{}", root.path("message").asText(""));
                return Map.of();
            }
            Map<Long, String> values = new HashMap<>();
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return Map.of();
            }
            for (JsonNode item : data) {
                long userId = item.path("userId").asLong(-1);
                JsonNode value = item.path("value");
                if (userId > 0 && !value.isNull() && value.isTextual()
                        && userIds.contains(userId)) {
                    values.put(userId, value.asText());
                }
            }
            return values;
        } catch (Exception error) {
            log.warn("用户配置响应解析失败（按无配置处理）：reason={}", error.getMessage());
            return Map.of();
        }
    }

    /**
     * 构造参数错误业务异常（供解析器使用，保持单一异常口径）
     *
     * @param message 文案
     * @return 业务异常
     */
    static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }
}
