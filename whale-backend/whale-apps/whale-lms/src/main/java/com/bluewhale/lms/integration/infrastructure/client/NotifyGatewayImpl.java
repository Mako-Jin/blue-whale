package com.bluewhale.lms.integration.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.integration.domain.gateway.NotifyGateway;
import com.bluewhale.lms.integration.domain.model.NotifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知投递网关实现。
 *
 * <p>只调用 {@code POST /message/v1/internal/send}。幂等键由应用层按场景生成，
 * 消息服务侧按 {@code (tenantId, idempotencyKey, recipient)} 二次去重——
 * 两侧都做去重并非冗余：LMS 侧的去重保证「本地不重发」，消息服务侧的去重
 * 保证「即使 LMS 重试到同一键也只投一次」，网络重试恰好发生在后者。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyGatewayImpl implements NotifyGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 消息服务名 */
    private static final String SERVICE_NAME = "whale-message";

    /** 通知渠道：站内信 */
    private static final List<String> DEFAULT_CHANNELS = List.of("INAPP");

    private final DiscoveryClient discoveryClient;

    @Override
    public void send(NotifyRequest request) {
        request.validate();
        Long tenantId = TenantContext.get() == null ? TenantContext.DEFAULT_TENANT_ID : TenantContext.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", request.getBizType());
        body.put("bizId", request.getBizId());
        body.put("idempotencyKey", request.getIdempotencyKey());
        body.put("templateCode", request.getTemplateCode());
        body.put("title", request.getTitle());
        body.put("content", request.getContent());
        body.put("channels", DEFAULT_CHANNELS);
        body.put("recipients", request.getRecipients().stream().distinct()
                .map(userId -> Map.of("userId", userId)).toList());
        Map<?, ?> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/message/v1/internal/send")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_FAILED, "消息服务响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_FAILED,
                    "消息服务返回失败：" + response.get("message"));
        }
    }

    /**
     * 解析消息服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
