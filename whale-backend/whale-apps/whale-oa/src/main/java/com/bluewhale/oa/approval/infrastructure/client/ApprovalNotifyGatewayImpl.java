package com.bluewhale.oa.approval.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.approval.domain.gateway.ApprovalNotifyGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalNotifyMessage;
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
 * 审批通知投递客户端实现。
 *
 * <p>只调用 {@code POST /message/v1/internal/send}；幂等键由审批应用服务生成，
 * 重复投递由消息服务按 {@code (tenantId, idempotencyKey)} 去重。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNotifyGatewayImpl implements ApprovalNotifyGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 消息服务名 */
    private static final String SERVICE_NAME = "whale-message";

    /** 通知渠道：站内信 */
    private static final List<String> DEFAULT_CHANNELS = List.of("INAPP");

    private final DiscoveryClient discoveryClient;

    @Override
    public void send(ApprovalNotifyMessage message) {
        if (message == null || message.getUserIds() == null || message.getUserIds().isEmpty()) {
            return;
        }
        Long tenantId = TenantContext.get() == null ? TenantContext.DEFAULT_TENANT_ID : TenantContext.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", message.getBizType());
        body.put("bizId", message.getBizId());
        body.put("idempotencyKey", message.getIdempotencyKey());
        body.put("templateCode", message.getTemplateCode());
        body.put("title", message.getTitle());
        body.put("content", message.getContent());
        body.put("payload", message.getPayload());
        body.put("channels", DEFAULT_CHANNELS);
        body.put("recipients", message.getUserIds().stream().distinct()
                .map(id -> Map.of("userId", id)).toList());
        Map<?, ?> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/message/v1/internal/send")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.oa.approval.message-service-returned-empty-response", "消息服务响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.oa.approval.failed-message-service-return-message",
                    "消息服务返回失败: {0}", response.get("message"));
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
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.oa.approval.message-service-unavailable", "消息服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
