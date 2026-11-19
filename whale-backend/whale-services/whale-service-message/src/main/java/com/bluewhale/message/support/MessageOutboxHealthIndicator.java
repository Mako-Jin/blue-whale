package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import com.bluewhale.message.po.MessageCallbackTaskPo;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 回调 outbox 健康检查：积压的待派发任务超过阈值时报告 DOWN。
 *
 * <p>阈值由 {@code whale.message.receipt.pending-alert-threshold} 配置，默认 1000。
 * 积压意味着回调投递跟不上回执产生速度，需要人工确认下游业务方是否可用。</p>
 *
 * @author BlueWhale
 */
@Component("messageOutbox")
@RequiredArgsConstructor
public class MessageOutboxHealthIndicator implements HealthIndicator {

    /** 待派发任务状态 */
    private static final String TASK_PENDING = "PENDING";

    private final MessageCallbackTaskMapper callbackTaskMapper;

    private final MessageReceiptProperties properties;

    @Override
    public Health health() {
        LambdaQueryWrapper<MessageCallbackTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageCallbackTaskPo::getStatus, TASK_PENDING);
        Long pending = callbackTaskMapper.selectCount(wrapper);
        long count = pending == null ? 0L : pending;
        Health.Builder builder = count > properties.getPendingAlertThreshold() ? Health.down() : Health.up();
        return builder.withDetail("pending", count)
                .withDetail("threshold", properties.getPendingAlertThreshold())
                .build();
    }
}
