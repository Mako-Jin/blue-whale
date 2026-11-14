package com.bluewhale.workflow.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.workflow.config.WorkflowTimeoutProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-message 内网发送客户端（站内信通道）。
 *
 * <p>复用 {@code POST /message/internal/send}；网关对 {@code /*&#47;internal/**} 统一 403，
 * 调用必须带内网令牌。与 forum/twin 同口径：<b>只负责发</b>，失败返回 {@code false} 不抛异常。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class WorkflowMessageClient {

    /** 内网发送路径 */
    private static final String SEND_PATH = "/message/internal/send";

    /** 站内信通道 */
    private static final String CHANNEL_INAPP = "INAPP";

    /** 业务类型（whale-message 仅做长度限制，不解释业务语义） */
    private static final String BIZ_TYPE = "workflow-timeout";

    private final WorkflowTimeoutProperties properties;

    /** 测试注入的客户端；为空时按当前内网令牌配置现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：自建带超时的客户端，避免 message 不可达时阻塞调度线程。
     *
     * @param properties 超时与提醒配置
     */
    @Autowired
    public WorkflowMessageClient(WorkflowTimeoutProperties properties) {
        this(properties, buildTimeoutClient(properties));
    }

    /**
     * 构造带连接/读超时的内网客户端。
     *
     * @param properties 超时与提醒配置
     * @return 客户端
     */
    private static RestClient buildTimeoutClient(WorkflowTimeoutProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return InternalTokenSupport.builder().requestFactory(factory).build();
    }

    /**
     * 测试用构造。
     *
     * @param properties 超时与提醒配置
     * @param client 外部客户端（可绑定 MockRestServiceServer）
     */
    WorkflowMessageClient(WorkflowTimeoutProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 发送一条超时提醒站内信。
     *
     * @param tenantId 租户ID
     * @param idempotencyKey 幂等键（同一任务同一时间窗固定不变）
     * @param title 标题
     * @param content 正文
     * @param receiverId 接收人用户ID
     * @return 投递成功返回 {@code true}；任何异常返回 {@code false}（不抛出）
     */
    public boolean sendTimeoutReminder(Long tenantId, String idempotencyKey, String title,
                                       String content, Long receiverId) {
        if (tenantId == null || receiverId == null) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", BIZ_TYPE);
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
                    .headers(headers -> headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId)))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return response != null && "0".equals(String.valueOf(response.get("code")));
        } catch (RuntimeException error) {
            log.warn("超时提醒投递失败：idempotencyKey={}, reason={}",
                    idempotencyKey, error.getMessage());
            return false;
        }
    }
}
