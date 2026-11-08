package com.bluewhale.crm.marketing.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.marketing.domain.model.MarketingMessage;
import com.bluewhale.crm.marketing.domain.model.SendChannel;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchPort;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** 响应体数据节点 */
    private static final String FIELD_DATA = "data";

    /** 响应体消息ID节点 */
    private static final String FIELD_MESSAGE_ID = "messageId";

    /** 响应体投递明细节点 */
    private static final String FIELD_DELIVERIES = "deliveries";

    /** 投递失败状态 */
    private static final String STATUS_FAILED = "FAILED";

    /** 投递跳过状态 */
    private static final String STATUS_SKIPPED = "SKIPPED";

    /** 渠道 mock 状态（未发起真实投递，不可视为成功） */
    private static final String STATUS_MOCKED = "MOCKED";

    /** 业务类型 */
    private static final String BIZ_TYPE = "CRM_MARKETING";

    /** 消息服务站内信渠道编码 */
    private static final String CHANNEL_INAPP = "INAPP";

    /** 标题列宽（消息服务 title 最长 128） */
    private static final int TITLE_MAX_LENGTH = 128;

    /** 幂等键列宽（消息服务 idempotencyKey 最长 96） */
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 96;

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
        if (message.getChannel() == null) {
            return MessageDispatchResult.failed("触达渠道为空");
        }
        String channelCode = toMessageChannel(message.getChannel());
        Map<String, Object> recipient = recipientOf(message.getChannel(), message.getRecipient().trim());
        if (recipient == null) {
            return MessageDispatchResult.failed("站内信收件人必须是有效用户 ID");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (message.getContentDocId() != null) {
            payload.put("contentDocId", String.valueOf(message.getContentDocId()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId());
        body.put("bizType", BIZ_TYPE);
        body.put("bizId", message.getSendId() == null ? null : String.valueOf(message.getSendId()));
        body.put("idempotencyKey", idempotencyKey(message, channelCode));
        body.put("templateCode", message.getTemplateCode());
        body.put("title", truncate(message.getSubject(), TITLE_MAX_LENGTH));
        body.put("payload", payload);
        body.put("channels", List.of(channelCode));
        body.put("recipients", List.of(recipient));
        if (properties.getCallbackUrl() != null && !properties.getCallbackUrl().isBlank()) {
            body.put("callbackUrl", properties.getCallbackUrl());
        }
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
            return judgeDeliveries(response.get(FIELD_DATA));
        } catch (RuntimeException exception) {
            return MessageDispatchResult.failed("消息服务不可用：" + exception.getMessage());
        }
    }

    /**
     * 判定投递明细：全部成功才算 SENT；出现失败/跳过时返回失败原因，不伪造成功。
     *
     * <p>成功时回填 {@code messageId} 与渠道侧 {@code providerMessageId}，供后续
     * 送达回执回调反查触达明细。</p>
     *
     * @param data 响应体 data 节点
     * @return 投递结果
     */
    @SuppressWarnings("unchecked")
    private MessageDispatchResult judgeDeliveries(Object data) {
        if (!(data instanceof Map<?, ?> result)) {
            return MessageDispatchResult.sent();
        }
        Long messageId = parseMessageId(((Map<String, Object>) result).get(FIELD_MESSAGE_ID));
        Object deliveries = ((Map<String, Object>) result).get(FIELD_DELIVERIES);
        String providerMessageId = null;
        if (deliveries instanceof List<?> list) {
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> delivery)) {
                    continue;
                }
                Object status = delivery.get("status");
                if (STATUS_MOCKED.equals(status)) {
                    return MessageDispatchResult.failed("消息通道处于 mock 模式，未真实投递");
                }
                if (STATUS_FAILED.equals(status) || STATUS_SKIPPED.equals(status)) {
                    Object error = delivery.get("errorMessage");
                    return MessageDispatchResult.failed(error == null
                            ? "消息投递结果 " + status : String.valueOf(error));
                }
                if (providerMessageId == null) {
                    Object providerId = delivery.get("providerMessageId");
                    providerMessageId = providerId == null ? null : String.valueOf(providerId);
                }
            }
        }
        return MessageDispatchResult.sent(messageId, providerMessageId);
    }

    /**
     * 解析消息服务返回的消息ID。
     *
     * @param node 节点值
     * @return 消息ID；无法解析返回 {@code null}
     */
    private Long parseMessageId(Object node) {
        if (node instanceof Number number) {
            return number.longValue();
        }
        if (node instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    /**
     * CRM 触达渠道转消息服务渠道编码。
     *
     * @param channel CRM 渠道
     * @return 消息服务渠道编码
     */
    private String toMessageChannel(SendChannel channel) {
        return channel == SendChannel.INBOX ? CHANNEL_INAPP : channel.name();
    }

    /**
     * 按渠道构造接收人。
     *
     * @param channel CRM 渠道
     * @param recipient 收件人
     * @return 接收人节点；站内信收件人非数字时返回 {@code null}
     */
    private Map<String, Object> recipientOf(SendChannel channel, String recipient) {
        Map<String, Object> node = new LinkedHashMap<>();
        switch (channel) {
            case EMAIL -> node.put("email", recipient);
            case SMS -> node.put("phone", recipient);
            default -> {
                try {
                    node.put("userId", Long.parseLong(recipient));
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
        }
        return node;
    }

    /**
     * 幂等键：同一活动同一联系人同一渠道只投递一次（消息服务按租户内幂等键去重）。
     *
     * @param message 营销消息
     * @param channelCode 消息服务渠道编码
     * @return 幂等键（最长 96）
     */
    private String idempotencyKey(MarketingMessage message, String channelCode) {
        String raw = BIZ_TYPE + ":" + message.getSendId() + ":" + channelCode + ":" + message.getRecipient();
        return truncate(raw, IDEMPOTENCY_KEY_MAX_LENGTH);
    }

    /**
     * 按列宽截断。
     *
     * @param value 原值
     * @param maxLength 最大长度
     * @return 截断后的值
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 当前租户 ID。
     *
     * @return 租户 ID
     */
    private Long tenantId() {
        Long tenantId = TenantContext.get();
        return tenantId == null ? DEFAULT_TENANT_ID : tenantId;
    }

    /**
     * 当前租户头值。
     *
     * @return 租户 ID 文本
     */
    private String tenantHeader() {
        return String.valueOf(tenantId());
    }
}
