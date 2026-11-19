package com.bluewhale.forum.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.forum.config.ForumProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-message 内网发送客户端（站内信通道）。
 *
 * <p>复用平台既有 {@code POST /message/v1/internal/send}：网关对 {@code /*&#47;internal/**}
 * 统一 403，外部不可达；调用必须带内网令牌（{@link InternalTokenSupport}）。
 * 与 twin 的实现同口径——本类<b>只负责「发」</b>，失败不抛异常，
 * 由 {@code ForumNotifyService} 决定落 outbox 还是放弃。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ForumNotifyClient {

    /** 内网发送路径 */
    private static final String SEND_PATH = "/message/v1/internal/send";

    /** 站内信通道 */
    private static final String CHANNEL_INAPP = "INAPP";

    private final ForumProperties properties;

    /** 测试注入的客户端；为空时按当前内网令牌配置现场创建 */
    private final RestClient client;

    /**
     * 生产用构造。
     *
     * <p>不在构造期创建 RestClient：本 Bean 可能早于内网令牌自动配置完成初始化，
     * 那样拦截器会拿到未绑定的默认配置，令牌发不出去。</p>
     *
     * @param properties 论坛配置
     */
    @Autowired
    public ForumNotifyClient(ForumProperties properties) {
        this(properties, null);
    }

    /**
     * 测试用构造。
     *
     * @param properties 论坛配置
     * @param client 外部客户端（可绑定 MockRestServiceServer）
     */
    ForumNotifyClient(ForumProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 发送一条站内信。
     *
     * @param tenantId 租户ID
     * @param idempotencyKey 幂等键（同一事件固定不变）
     * @param title 标题
     * @param content 正文
     * @param receiverId 接收人
     * @return 投递成功返回 {@code true}；任何异常返回 {@code false}（不抛出）
     */
    public boolean sendInapp(Long tenantId, String idempotencyKey, String title, String content,
                             Long receiverId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", "forum-notify");
        body.put("idempotencyKey", idempotencyKey);
        body.put("title", title);
        body.put("content", content);
        body.put("channels", List.of(CHANNEL_INAPP));
        body.put("recipients", List.of(Map.of("userId", receiverId)));
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            Map<String, Object> response = target.post()
                    .uri(properties.getMessageBaseUrl() + SEND_PATH)
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
            return isSuccess(response);
        } catch (RuntimeException error) {
            log.warn("站内信投递失败：idempotencyKey={}, reason={}", idempotencyKey, error.getMessage());
            return false;
        }
    }

    /**
     * 解析平台统一信封 {@code {code, message, data}}。
     *
     * @param response 响应体
     * @return 业务码为 0 返回 {@code true}
     */
    private boolean isSuccess(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object code = response.get("code");
        return code != null && "0".equals(String.valueOf(code));
    }
}
