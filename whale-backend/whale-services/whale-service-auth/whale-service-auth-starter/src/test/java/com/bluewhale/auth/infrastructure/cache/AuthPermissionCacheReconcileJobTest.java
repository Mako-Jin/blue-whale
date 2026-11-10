package com.bluewhale.auth.infrastructure.cache;

import com.bluewhale.auth.infrastructure.config.PermissionCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuthPermissionCacheReconcileJob} 单元测试：开关、重对齐调用与异常隔离。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuthPermissionCacheReconcileJobTest {

    @Mock
    private AuthPermissionCacheInvalidator invalidator;

    private PermissionCacheProperties properties;

    private AuthPermissionCacheReconcileJob job;

    @BeforeEach
    void setUp() {
        properties = new PermissionCacheProperties();
        job = new AuthPermissionCacheReconcileJob(invalidator, properties);
    }

    @Test
    @DisplayName("reconcile-enabled=false：不触发重对齐")
    void shouldSkipWhenDisabled() {
        properties.setReconcileEnabled(false);

        job.reconcile();

        verifyNoInteractions(invalidator);
    }

    @Test
    @DisplayName("启用时调用 flushPending")
    void shouldFlushPendingWhenEnabled() {
        when(invalidator.flushPending()).thenReturn(2);

        job.reconcile();

        verify(invalidator).flushPending();
        verify(invalidator).pendingCount();
    }

    @Test
    @DisplayName("无待处理用户时不读取待处理计数")
    void shouldNotQueryPendingCountWhenNothingCleared() {
        when(invalidator.flushPending()).thenReturn(0);

        job.reconcile();

        verify(invalidator, never()).pendingCount();
    }

    @Test
    @DisplayName("重对齐异常不向调度线程抛出")
    void shouldSwallowException() {
        when(invalidator.flushPending()).thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(job::reconcile);
    }
}
