package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocArchiveEvent;
import com.bluewhale.doc.application.service.DocArchiveService;
import com.bluewhale.doc.config.DocProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 页面归档事件监听器（M3）。
 *
 * <p>主事务提交后入档；外部服务不可用只记录告警，不影响页面归档结果。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocArchiveListener {

    private final DocArchiveService archiveService;

    private final DocProperties docProperties;

    /**
     * 处理归档事件。
     *
     * @param event 归档事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArchive(DocArchiveEvent event) {
        if (!docProperties.isArchiveEnabled()) {
            return;
        }
        Long previous = TenantContext.get();
        try {
            TenantContext.set(event.tenantId());
            archiveService.archiveSnapshot(event.tenantId(), event.pageId(), event.userId());
        } catch (RuntimeException ex) {
            log.warn("页面归档快照失败，已降级: pageId={}, error={}", event.pageId(), ex.getMessage());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }
}
