package com.bluewhale.pm.application.notify;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pm.infrastructure.client.ImNotifyClient;
import com.bluewhale.pm.infrastructure.config.PmIntegrationConfig;
import com.bluewhale.pm.infrastructure.config.PmNotifyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 站内通知发布器：事务内发布、提交后异步投递。
 *
 * <p>{@code whale.pm.notify.enabled=false} 时发布方法直接返回（零副作用）；
 * 投递异常只记录告警，绝不回滚业务事务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmNotifyPublisher {

    /** 业务类型（IM 侧幂等键与排障用） */
    private static final String BIZ_TYPE = "PM";

    private final PmNotifyProperties properties;

    private final ApplicationEventPublisher eventPublisher;

    private final ImNotifyClient notifyClient;

    /**
     * 发布通知事件：关闭态直接返回；无有效接收人时丢弃。
     *
     * @param event 通知事件
     */
    public void publish(PmNotifyEvent event) {
        if (!properties.isEnabled() || event == null) {
            return;
        }
        List<Long> recipients = event.getRecipientIds() == null ? List.of() : event.getRecipientIds().stream()
                .filter(Objects::nonNull)
                .filter(userId -> !userId.equals(event.getOperatorId()))
                .distinct()
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        event.setRecipientIds(recipients);
        event.setTenantId(TenantContext.get());
        eventPublisher.publishEvent(event);
    }

    /**
     * 事务提交后异步投递站内通知。
     *
     * @param event 通知事件
     */
    @Async(PmIntegrationConfig.NOTIFY_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(PmNotifyEvent event) {
        try {
            TenantContext.set(event.getTenantId());
            notifyClient.notify(event.getRecipientIds(), event.getKind().title(), content(event), BIZ_TYPE,
                    String.valueOf(event.getWorkItemId()), clientMsgId(event));
        } catch (Exception e) {
            log.warn("站内通知投递失败: workItemId={}, kind={}, error={}",
                    event.getWorkItemId(), event.getKind(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 拼装通知正文：工作项键 + 标题 + 明细。
     *
     * @param event 通知事件
     * @return 通知正文
     */
    private String content(PmNotifyEvent event) {
        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(event.getItemKey())) {
            text.append(event.getItemKey()).append(' ');
        }
        if (StringUtils.hasText(event.getItemTitle())) {
            text.append(event.getItemTitle());
        }
        if (StringUtils.hasText(event.getDetail())) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(event.getDetail().trim());
        }
        return text.toString();
    }

    /**
     * 构造 IM 侧幂等键。
     *
     * @param event 通知事件
     * @return 幂等键（不超过 64 字符）
     */
    private String clientMsgId(PmNotifyEvent event) {
        return "pm-" + event.getKind().name().toLowerCase() + "-" + event.getWorkItemId()
                + "-" + System.currentTimeMillis();
    }
}
