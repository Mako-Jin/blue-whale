package com.bluewhale.audit.mq.support;

import com.bluewhale.common.audit.properties.AuditMqProperties;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 审计死信队列人工重放（AUTH-10）。
 *
 * <p>用于毒消息修复后把 {@code bluewhale.audit.dlq} 中的消息重新投递回业务交换机。
 * 采用「basicGet 不自动确认 → basicPublish 原样重投 → basicAck」的顺序，
 * 重投失败时 {@code basicNack(message, requeue=true)} 放回死信队列，避免消息丢失。
 * 原消息属性（含 {@code messageId} 幂等键）通过 {@code BasicProperties} 原样保留，
 * 保证重放后仍可被去重表正确识别。</p>
 *
 * <p>单次最多重放 100 条，防止误操作一次打爆业务队列；重放后的消息可能再次进入 DLQ，
 * 需先修复根因再重放。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.audit.mq", name = "consume-enabled", havingValue = "true")
public class AuditDlqReplayService {

    /** 单次重放上限 */
    private static final int MAX_REPLAY_LIMIT = 100;

    private final RabbitTemplate rabbitTemplate;

    private final AuditMqProperties properties;

    /**
     * 重放死信队列消息
     *
     * @param limit 期望重放条数；收敛到 {@code [1, 100]}
     * @return 实际重放条数（队列空或重投失败时提前结束）
     */
    public int replay(int limit) {
        int effective = Math.min(Math.max(limit, 1), MAX_REPLAY_LIMIT);
        int replayed = 0;
        for (int index = 0; index < effective; index++) {
            Boolean published = rabbitTemplate.execute(channel -> {
                GetResponse response = channel.basicGet(properties.getDeadLetterQueue(), false);
                if (response == null) {
                    return false;
                }
                long deliveryTag = response.getEnvelope().getDeliveryTag();
                try {
                    channel.basicPublish(properties.getExchange(), properties.getRoutingKey(),
                            response.getProps(), response.getBody());
                    channel.basicAck(deliveryTag, false);
                    return true;
                } catch (IOException | RuntimeException e) {
                    channel.basicNack(deliveryTag, false, true);
                    throw e;
                }
            });
            if (!Boolean.TRUE.equals(published)) {
                break;
            }
            replayed++;
        }
        log.info("审计死信队列重放完成: 请求条数={}, 实际重放={}, queue={}",
                effective, replayed, properties.getDeadLetterQueue());
        return replayed;
    }
}
