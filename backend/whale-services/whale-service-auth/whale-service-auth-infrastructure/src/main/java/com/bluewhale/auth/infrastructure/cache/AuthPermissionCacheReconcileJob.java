package com.bluewhale.auth.infrastructure.cache;

import com.bluewhale.auth.config.PermissionCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 权限缓存断连重对齐任务（AUTH-20 / REQ-DIST-003）。
 *
 * <p>定时重试 {@link AuthPermissionCacheInvalidator} 中因 Redis 断连未能删除的用户缓存：
 * Redis 恢复后无需等待「下一次权限变更」或 TTL 自然过期，即可完成失效。待处理为空时不产生
 * Redis 访问。</p>
 *
 * <p>默认 30 秒执行一次，可通过
 * {@code whale.auth.permission-cache.reconcile-interval-ms} 与
 * {@code reconcile-initial-delay-ms} 覆盖，或置 {@code reconcile-enabled=false} 关闭。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthPermissionCacheReconcileJob {

    private final AuthPermissionCacheInvalidator invalidator;

    private final PermissionCacheProperties properties;

    /**
     * 重试待失效用户缓存；异常仅记录日志，避免调度线程中断
     */
    @Scheduled(
            fixedDelayString = "${whale.auth.permission-cache.reconcile-interval-ms:30000}",
            initialDelayString = "${whale.auth.permission-cache.reconcile-initial-delay-ms:10000}")
    public void reconcile() {
        if (!properties.isReconcileEnabled()) {
            return;
        }
        try {
            int cleared = invalidator.flushPending();
            if (cleared > 0) {
                log.info("权限缓存断连重对齐完成: 清除待失效用户={}, 剩余={}",
                        cleared, invalidator.pendingCount());
            }
        } catch (Exception ex) {
            log.warn("权限缓存断连重对齐执行失败: {}", ex.getMessage());
        }
    }
}
