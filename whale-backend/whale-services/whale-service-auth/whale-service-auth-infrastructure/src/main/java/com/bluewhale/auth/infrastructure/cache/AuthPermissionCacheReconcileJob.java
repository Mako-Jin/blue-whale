package com.bluewhale.auth.infrastructure.cache;

import com.bluewhale.auth.infrastructure.config.PermissionCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 权限缓存断连重对齐任务（AUTH-20 / REQ-DIST-003）。
 *
 * <p>定时重试 {@link AuthPermissionCacheInvalidator} 中因 Redis 断连未能删除的用户缓存：
 * Redis 恢复后无需等待「下一次权限变更」或 TTL 自然过期，即可完成失效。待处理为空时不产生
 * Redis 访问。</p>
 *
 * <p>间隔与首次延迟以 SpEL 引用 {@link PermissionCacheProperties} Bean（单一配置来源）；
 * {@code whale.auth.permission-cache.reconcile-enabled=false} 时整个 Bean 不注册，
 * 不占用调度线程（运行时守卫保留作为二次兜底）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "whale.auth.permission-cache.reconcile-enabled",
        havingValue = "true", matchIfMissing = true)
public class AuthPermissionCacheReconcileJob {

    private final AuthPermissionCacheInvalidator invalidator;

    private final PermissionCacheProperties properties;

    /**
     * 重试待失效用户缓存；异常仅记录日志，避免调度线程中断
     */
    @Scheduled(
            fixedDelayString = "#{@permissionCacheProperties.reconcileIntervalMs}",
            initialDelayString = "#{@permissionCacheProperties.reconcileInitialDelayMs}")
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
