package com.bluewhale.pdm.change.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-message 内网发送客户端实现
 *
 * <p>只调用 {@code POST /message/v1/internal/send}；幂等键由 PDM 生成，
 * 重复投递由消息服务按 {@code (tenantId, idempotencyKey)} 去重。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PdmChgMessageClientImpl implements PdmChgMessageClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 消息服务名 */
    private static final String SERVICE_NAME = "whale-message";

    private final DiscoveryClient discoveryClient;

    @Override
    public void send(Long tenantId, String bizType, String bizId, String idempotencyKey,
                     String title, String content, List<String> channels, List<Long> userIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("bizType", bizType);
        body.put("bizId", bizId);
        body.put("idempotencyKey", idempotencyKey);
        body.put("title", title);
        body.put("content", content);
        body.put("channels", channels);
        body.put("recipients", userIds.stream().map(id -> Map.of("userId", id)).toList());
        Map<?, ?> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/message/v1/internal/send")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息服务响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "消息服务返回失败: " + response.get("message"));
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
        return instances.get(0).getUri().toString();
    }
}
