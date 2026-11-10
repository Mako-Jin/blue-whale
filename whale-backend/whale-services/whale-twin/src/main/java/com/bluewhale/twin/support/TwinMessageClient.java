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
 * <p>复用平台既有的 {@code POST /message/internal/send}（网关对 {@code /internal/**}
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
    private static final String SEND_PATH = "/message/internal/send";

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
        Object messageId = detail.get("messageId");
        Object status = detail.get("status");
        Long id = messageId instanceof Number number ? number.longValue() : null;
        return new SendOutcome(id, status == null ? null : String.valueOf(status), null);
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
