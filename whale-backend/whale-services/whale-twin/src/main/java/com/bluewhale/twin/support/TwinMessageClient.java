package com.bluewhale.twin.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.config.TwinProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-message 内网发送客户端（Wave 6.10 M1）：站内信通道。
 *
 * <p>复用平台既有的 {@code POST /message/v1/internal/send}（网关对 {@code /internal/**}
 * 统一 403，外部不可达），请求经 {@link InternalTokenSupport} 自动带内网令牌，
 * 并以 {@code X-Tenant-Id} 透传租户——本通道与 webhook 相反，
 * <b>必须</b>走内网令牌，因为目标是平台内部服务。</p>
 *
 * <p>幂等由 {@code idempotencyKey} 保证：同一告警重复投递时 whale-message 返回
 * {@code duplicate=true}，不会重复打扰接收人。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TwinMessageClient {

    /** 内网发送路径 */
    private static final String SEND_PATH = "/message/v1/internal/send";

    /** 内网偏好解析路径（Wave 6.17c） */
    private static final String PREFERENCE_RESOLVE_PATH = "/message/v1/internal/preferences/resolve";

    /** 业务类型（whale-message 侧归类用，最长 32） */
    private static final String BIZ_TYPE = "twin-alert";

    /** 站内信通道 */
    private static final String CHANNEL_INAPP = "INAPP";

    /** 成功业务码 */
    private static final String CODE_SUCCESS = "0";

    private final TwinProperties properties;

    /** 测试注入的客户端；为空时每次调用按当前令牌配置新建（避免早于自动配置初始化） */
    private final RestClient client;

    /**
     * 生产用构造：客户端在调用时按 {@link InternalTokenSupport} 当前配置创建。
     *
     * <p>不在构造期创建：本 Bean 可能早于内网令牌自动配置完成初始化，
     * 那样拦截器会拿到未绑定的默认配置，令牌反而发不出去。</p>
     *
     * <p>类里有测试用的第二个构造器，必须显式标注 {@code @Autowired}，
     * 否则 Spring 会去找无参构造而启动失败。</p>
     *
     * @param properties 孪生配置
     */
    @Autowired
    public TwinMessageClient(TwinProperties properties) {
        this(properties, null);
    }

    /**
     * 测试用构造：外部传入 RestClient（可绑定 MockRestServiceServer）。
     *
     * @param properties 孪生配置
     * @param client     调用客户端（为空表示按内网令牌现场创建）
     */
    TwinMessageClient(TwinProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 发送一条站内信。
     *
     * @param tenantId       租户ID
     * @param idempotencyKey 幂等键（同一告警固定不变）
     * @param title          标题
     * @param content        正文
     * @param payload        结构化载荷（与 SSE alert 帧同源）
     * @param userIds        接收人用户ID（非空）
     * @return 投递结果；任何异常都折算为失败结果，不抛出
     */
    public SendOutcome sendInapp(Long tenantId, String idempotencyKey, String title, String content,
                                 Map<String, Object> payload, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new SendOutcome(null, null, "无接收人");
        }
        String baseUrl = properties.getAlertNotify().getMessageBaseUrl();
        Map<String, Object> body = buildBody(tenantId, idempotencyKey, title, content, payload, userIds);
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            Map<String, Object> response = target.post()
                    .uri(baseUrl + SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (tenantId != null) {
                            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return parse(response);
        } catch (RuntimeException error) {
            log.warn("站内信投递失败（不重试，等下次级别变化）：sceneAlertKey={}, reason={}",
                    idempotencyKey, error.getMessage());
            return new SendOutcome(null, null, error.getMessage());
        }
    }

    /**
     * 按用户偏好解析站内信可投收件人（Wave 6.17c）。
     *
     * <p>语义解释在 whale-message（静默窗口/阈值/渠道归一），孪生侧只声明默认行为：
     * 渠道 INAPP、级别用本次告警级别；critical 穿透由全局开关转成
     * {@code quietBypassLevels}。任何失败（message 不可达 / 返回异常）返回
     * {@code null} 表示「解析不了」，调用方按<b>全部放行</b>处理——偏好是旁路，
     * 绝不阻塞外发（fail-open）。</p>
     *
     * @param tenantId      租户ID
     * @param userIds       候选接收人
     * @param level         本次告警级别（warn/critical，大小写不敏感）
     * @param criticalBypass 全局 critical 穿透开关（true 时静默对 critical 不生效）
     * @return 仍可投站内信的用户 ID；解析失败返回 {@code null}（全部放行）
     */
    public List<Long> resolveInappRecipients(Long tenantId, List<Long> userIds, String level,
                                             boolean criticalBypass) {
        if (userIds == null || userIds.isEmpty()) {
            return userIds;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", BIZ_TYPE);
        body.put("userIds", userIds);
        if (StringUtils.hasText(level)) {
            body.put("level", level.trim().toUpperCase(java.util.Locale.ROOT));
        }
        body.put("defaults", Map.of("channels", List.of(CHANNEL_INAPP)));
        if (criticalBypass && StringUtils.hasText(level)
                && "CRITICAL".equalsIgnoreCase(level.trim())) {
            body.put("quietBypassLevels", List.of("CRITICAL"));
        }
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            Map<String, Object> response = target.post()
                    .uri(properties.getAlertNotify().getMessageBaseUrl() + PREFERENCE_RESOLVE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (tenantId != null) {
                            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return parseAllowedUsers(response, userIds);
        } catch (RuntimeException error) {
            log.warn("用户偏好解析失败（按全部放行处理）：reason={}", error.getMessage());
            return null;
        }
    }

    /**
     * 解析偏好结果：保留仍可投站内信的用户。
     *
     * @param response       响应信封
     * @param requestedUsers 请求的候选用户
     * @return 可投用户；业务失败或结构不符返回 {@code null}（全部放行）
     */
    private List<Long> parseAllowedUsers(Map<String, Object> response, List<Long> requestedUsers) {
        if (response == null) {
            return null;
        }
        Object code = response.get("code");
        if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
            log.warn("用户偏好解析业务失败（按全部放行处理）：{}", response.get("message"));
            return null;
        }
        if (!(response.get("data") instanceof List<?> items)) {
            return null;
        }
        List<Long> allowed = new java.util.ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> decision)) {
                continue;
            }
            // 平台全局把 Long 序列化成字符串（防前端精度丢失），这里必须两种形态都认：
            // 只判 Number 会让每个接收人都解不出来 → 静默丢空群（表现为全部被偏好过滤）
            Long id = asLong(decision.get("userId"));
            Object channels = decision.get("channels");
            if (id == null || !(channels instanceof List<?> names)) {
                continue;
            }
            boolean inappAllowed = names.stream()
                    .anyMatch(name -> CHANNEL_INAPP.equalsIgnoreCase(String.valueOf(name)));
            if (inappAllowed && requestedUsers.contains(id)) {
                allowed.add(id);
            }
        }
        return allowed;
    }

    /**
     * 组装内网发送请求体（字段与 whale-message {@code SendMessageRequest} 对齐）。
     *
     * @param tenantId       租户ID
     * @param idempotencyKey 幂等键
     * @param title          标题
     * @param content        正文
     * @param payload        结构化载荷
     * @param userIds        接收人
     * @return 请求体
     */
    private Map<String, Object> buildBody(Long tenantId, String idempotencyKey, String title,
                                          String content, Map<String, Object> payload,
                                          List<Long> userIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", BIZ_TYPE);
        body.put("bizId", idempotencyKey);
        body.put("idempotencyKey", idempotencyKey);
        body.put("title", title);
        body.put("content", content);
        body.put("payload", payload);
        body.put("channels", List.of(CHANNEL_INAPP));
        body.put("recipients", userIds.stream().map(id -> Map.of("userId", id)).toList());
        return body;
    }

    /**
     * 解析发送结果：业务码非 0 视为失败。
     *
     * @param response 响应信封
     * @return 投递结果
     */
    @SuppressWarnings("unchecked")
    private SendOutcome parse(Map<String, Object> response) {
        if (response == null) {
            return new SendOutcome(null, null, "站内信接口返回空响应");
        }
        Object code = response.get("code");
        if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
            return new SendOutcome(null, null,
                    "站内信接口业务失败：" + response.get("message"));
        }
        Object data = response.get("data");
        if (!(data instanceof Map)) {
            return new SendOutcome(null, null, "站内信接口返回无 data");
        }
        Map<String, Object> detail = (Map<String, Object>) data;
        Long id = asLong(detail.get("messageId"));
        Object status = detail.get("status");
        return new SendOutcome(id, status == null ? null : String.valueOf(status), null);
    }

    /**
     * 宽松解析长整型：兼容 {@code Number} 与数值字符串（平台 Long 默认序列化为字符串）。
     *
     * @param raw 原始值
     * @return 数值；无法解析返回 {@code null}
     */
    private static Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 站内信投递结果。
     *
     * @param messageId whale-message 消息ID（失败为空）
     * @param status    消息状态（失败为空）
     * @param error     失败原因（成功为空）
     */
    public record SendOutcome(Long messageId, String status, String error) {

        /**
         * 是否投递成功。
         *
         * @return 无失败原因即视为成功
         */
        public boolean ok() {
            return !StringUtils.hasText(error);
        }
    }
}
