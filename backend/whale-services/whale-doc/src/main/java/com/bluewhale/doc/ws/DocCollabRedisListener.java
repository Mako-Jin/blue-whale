package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocCollabStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 跨实例协作帧监听（M2）。
 *
 * <p>订阅 {@code whale:doc:collab:channel:*}，忽略本实例发布的消息，把其他实例产生
 * 的 update / awareness / presence / leader / snapshot-ack 投递到本实例同页面连接。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.doc", name = "collab-enabled", havingValue = "true")
public class DocCollabRedisListener implements MessageListener {

    private final ObjectMapper objectMapper;

    private final DocCollabSessionRegistry registry;

    private final DocCollabStore collabStore;

    /**
     * 分发其他实例发布的协作帧。
     *
     * @param message Redis 消息
     * @param pattern 频道模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode envelope = objectMapper.readTree(body);
            if (collabStore.instanceId().equals(text(envelope, "origin"))) {
                return;
            }
            Long tenantId = envelope.path("tenantId").asLong();
            Long pageId = envelope.path("pageId").asLong();
            String frame = text(envelope, "frame");
            if (frame == null) {
                return;
            }
            registry.broadcastLocal(tenantId, pageId, frame,
                    text(envelope, "excludeClientId"), text(envelope, "targetClientId"));
        } catch (RuntimeException ex) {
            log.warn("跨实例协作帧处理失败: {}", ex.getMessage());
        }
    }

    /**
     * 读取 JSON 文本字段。
     *
     * @param node  节点
     * @param field 字段名
     * @return 文本；缺失返回 {@code null}
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
