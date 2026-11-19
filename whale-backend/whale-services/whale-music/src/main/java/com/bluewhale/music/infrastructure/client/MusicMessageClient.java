package com.bluewhale.music.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.repository.CommentNotificationPort;
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
 * whale-message 内网客户端（M4 评论回复 / 审核结果通知）。
 *
 * <p>只调用 {@code POST /message/v1/internal/send}；通知为旁路能力，
 * 任何失败（服务不可用、网络异常、返回失败）仅记录告警，绝不影响评论主事务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MusicMessageClient implements CommentNotificationPort {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 站内信渠道 */
    private static final List<String> CHANNELS = List.of("IN_APP");

    private final DiscoveryClient discoveryClient;

    private final MusicProperties musicProperties;

    @Override
    public void sendSafely(Long tenantId, String bizType, String bizId, String title, String content,
                           Long receiverId) {
        if (!musicProperties.isCommentNotifyEnabled() || receiverId == null) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", tenantId);
            body.put("bizType", bizType);
            body.put("bizId", bizId);
            body.put("idempotencyKey", bizType + ":" + bizId);
            body.put("title", title);
            body.put("content", content);
            body.put("channels", CHANNELS);
            body.put("recipients", List.of(Map.of("userId", receiverId)));
            Map<?, ?> response = InternalTokenSupport.restClient()
                    .post()
                    .uri(baseUrl() + "/message/v1/internal/send")
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object code = response == null ? null : response.get("code");
            if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
                log.warn("[MUSIC] 评论通知发送失败: bizType={}, bizId={}, receiver={}", bizType, bizId, receiverId);
            }
        } catch (Exception ex) {
            log.warn("[MUSIC] 评论通知发送异常: bizType={}, bizId={}, receiver={}, error={}",
                    bizType, bizId, receiverId, ex.getMessage());
        }
    }

    /**
     * 解析消息服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances =
                discoveryClient.getInstances(musicProperties.getMessageServiceName());
        if (instances.isEmpty()) {
            throw new IllegalStateException("whale-message 不可用");
        }
        return instances.get(0).getUri().toString();
    }
}
