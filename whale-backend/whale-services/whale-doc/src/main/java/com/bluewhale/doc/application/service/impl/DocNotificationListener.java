package com.bluewhale.doc.application.service.impl;

import com.bluewhale.doc.application.service.DocNotifyEvent;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.client.DocMessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 文档通知事件监听器（M3）。
 *
 * <p>事务提交后发送；外部服务异常只记录告警，不回滚主业务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocNotificationListener {

    private final DocMessageClient messageClient;

    private final DocProperties docProperties;

    /**
     * 处理文档通知事件。
     *
     * @param event 通知事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotify(DocNotifyEvent event) {
        if (!docProperties.isNotifyEnabled()) {
            return;
        }
        try {
            messageClient.send(event.tenantId(), event.bizType(), event.bizId(), event.idempotencyKey(),
                    event.title(), event.content(), channels(), event.recipientIds());
        } catch (RuntimeException ex) {
            log.warn("文档通知发送失败，已降级: key={}, error={}", event.idempotencyKey(), ex.getMessage());
        }
    }

    /**
     * 解析消息渠道配置。
     *
     * @return 渠道列表
     */
    private List<String> channels() {
        String configured = docProperties.getNotifyChannels();
        if (!StringUtils.hasText(configured)) {
            return List.of("INAPP");
        }
        return Arrays.stream(configured.split(",")).map(String::trim)
                .filter(StringUtils::hasText).toList();
    }
}
