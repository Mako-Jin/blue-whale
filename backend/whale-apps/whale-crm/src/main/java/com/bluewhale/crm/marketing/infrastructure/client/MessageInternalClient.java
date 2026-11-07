package com.bluewhale.crm.marketing.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.marketing.domain.model.MarketingMessage;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchPort;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * whale-message 内网投递客户端。
 *
 * <p>严格不伪造成功：营销开关关闭、消息服务异常或返回失败码时一律返回
 * {@code FAILED} 并携带明确原因。</p>
 *
 * @author BlueWhale
 */
@Component
public class MessageInternalClient implements MessageDispatchPort {

    /** 内网发送路径 */
    private static final String SEND_PATH = "/message/internal/send";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户 ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 营销配置 */
    private final CrmMarketingProperties properties;

    /** 内网客户端 */
    private final RestClient restClient;

    /**
     * 按营销配置构建客户端。
     *
     * @param properties 营销配置
     */
    public MessageInternalClient(CrmMarketingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getMessageUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public MessageDispatchResult dispatch(MarketingMessage message) {
        if (!properties.isEnabled()) {
            return MessageDispatchResult.failed("消息服务未启用");
        }
        if (message == null || message.getRecipient() == null || message.getRecipient().isBlank()) {
            return MessageDispatchResult.failed("收件人为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", message.getRecipient());
        body.put("channel", message.getChannel() == null ? null : message.getChannel().name());
        body.put("templateCode", message.getTemplateCode());
        body.put("subject", message.getSubject());
        body.put("contentDocId", message.getContentDocId());
        body.put("bizType", "CRM_MARKETING");
        body.put("bizId", message.getSendId());
        try {
            Map<String, Object> response = restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, tenantHeader())
                    .body(body).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                return MessageDispatchResult.failed("消息服务空响应");
            }
            Object code = response.get(FIELD_CODE);
            if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
                return MessageDispatchResult.failed(String.valueOf(response.get(FIELD_MESSAGE)));
            }
            return MessageDispatchResult.sent();
        } catch (RuntimeException exception) {
            return MessageDispatchResult.failed("消息服务不可用：" + exception.getMessage());
        }
    }

    /**
     * 当前租户头值。
     *
     * @return 租户 ID 文本
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }
}
