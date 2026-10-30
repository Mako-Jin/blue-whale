package com.bluewhale.pm.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.infrastructure.config.PmNotifyProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-im 内网站内通知客户端。
 *
 * <p>直连 {@code /im/internal/notify}（不经网关、无权限码头）；租户上下文存在时透传
 * {@code X-Tenant-Id}，由 IM 侧按租户隔离投递。</p>
 *
 * @author BlueWhale
 */
@Component
public class ImNotifyClient {

    /** 内网通知路径 */
    private static final String NOTIFY_PATH = "/im/internal/notify";

    private final RestClient restClient;

    /**
     * 按通知配置构建客户端。
     *
     * @param properties 通知配置
     */
    public ImNotifyClient(PmNotifyProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getImUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 投递站内通知。
     *
     * @param userIds   接收人用户ID
     * @param title     通知标题
     * @param content   通知正文
     * @param bizType   业务类型
     * @param bizId     业务标识
     * @param clientMsgId 幂等键
     */
    public void notify(List<Long> userIds, String title, String content, String bizType, String bizId,
                       String clientMsgId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userIds", userIds);
        body.put("title", title);
        body.put("content", content);
        body.put("bizType", bizType);
        body.put("bizId", bizId);
        body.put("clientMsgId", clientMsgId);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(NOTIFY_PATH)
                .contentType(MediaType.APPLICATION_JSON);
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            spec = spec.header(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        spec.body(body).retrieve().toBodilessEntity();
    }
}
