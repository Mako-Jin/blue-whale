package com.bluewhale.doc.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.doc.config.DocProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-message 内网客户端（M3 站内消息通知）。
 *
 * <p>只调用 {@code POST /message/internal/send}；幂等键由文档服务生成，
 * 重复通知由消息服务按 {@code (tenantId, idempotencyKey)} 去重。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocMessageClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    private final DiscoveryClient discoveryClient;

    private final DocProperties docProperties;

    /**
     * 发送站内消息。
     *
     * @param tenantId       租户ID
     * @param bizType        业务类型
     * @param bizId          业务标识
     * @param idempotencyKey 幂等键
     * @param title          标题
     * @param content        正文
     * @param channels       渠道
     * @param userIds        接收人ID
     * @return 发送结果
     */
    public SendOutcome send(Long tenantId, String bizType, String bizId, String idempotencyKey,
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
        Map<?, ?> response = RestClient.create()
                .post()
                .uri(baseUrl() + "/message/internal/send")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        Map<?, ?> data = extractData(response);
        boolean duplicate = Boolean.parseBoolean(String.valueOf(data.get("duplicate")));
        return new SendOutcome(duplicate, String.valueOf(data.get("messageId")));
    }

    /**
     * 解包响应信封。
     *
     * @param response 消息服务响应
     * @return 响应 data
     */
    private Map<?, ?> extractData(Map<?, ?> response) {
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息服务响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "消息服务返回失败: " + response.get("message"));
        }
        Object data = response.get("data");
        return data instanceof Map<?, ?> map ? map : Map.of();
    }

    /**
     * 解析消息服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(docProperties.getMessageServiceName());
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 消息发送结果。
     *
     * @param duplicate 是否命中幂等
     * @param messageId 消息ID
     */
    public record SendOutcome(boolean duplicate, String messageId) {
    }
}
