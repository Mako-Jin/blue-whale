package com.bluewhale.auth.application.tenant;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.security.token.TenantStatusKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TenantStatusCache} 单元测试：状态/到期时间写入、长期有效清理与 Redis 降级。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TenantStatusCacheTest {

    private static final long TENANT_ID = 2L;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TenantStatusCache cache;

    private static Tenant tenant(int status, LocalDateTime expireTime) {
        return Tenant.reconstitute(TENANT_ID, "acme", "ACME", "TENANT", status, expireTime,
                null, null, null);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("sync：启用且带到期时间时按 UTC 写入状态与到期 Unix 秒")
    void shouldSyncStatusAndExpireTime() {
        LocalDateTime expireTime = LocalDateTime.of(2027, 1, 1, 0, 0);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache.sync(tenant(Tenant.STATUS_ENABLED, expireTime));

        verify(valueOperations).set(TenantStatusKeys.statusKey(TENANT_ID), TenantStatusKeys.ENABLED);
        verify(valueOperations).set(TenantStatusKeys.expireKey(TENANT_ID),
                String.valueOf(expireTime.toEpochSecond(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("sync：停用且长期有效时写入停用状态并删除到期键")
    void shouldSyncDisabledAndDeleteExpireKey() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache.sync(tenant(Tenant.STATUS_DISABLED, null));

        verify(valueOperations).set(TenantStatusKeys.statusKey(TENANT_ID), TenantStatusKeys.DISABLED);
        verify(redisTemplate).delete(TenantStatusKeys.expireKey(TENANT_ID));
    }

    @Test
    @DisplayName("sync：Redis 不可用时仅记录告警，不抛出异常")
    void shouldNotThrowWhenRedisUnavailable() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOperations).set(anyString(), eq(TenantStatusKeys.ENABLED));

        assertDoesNotThrow(() -> cache.sync(tenant(Tenant.STATUS_ENABLED, null)));
    }

    @Test
    @DisplayName("sync：Redis 模板缺失时跳过写入")
    void shouldSkipWhenRedisTemplateAbsent() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

        cache.sync(tenant(Tenant.STATUS_ENABLED, null));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("sync：事务内延迟到提交后写入，提交前不触碰缓存")
    void shouldDeferWriteUntilTransactionCommit() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        cache.sync(tenant(Tenant.STATUS_DISABLED, null));

        verifyNoInteractions(valueOperations);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(valueOperations).set(TenantStatusKeys.statusKey(TENANT_ID), TenantStatusKeys.DISABLED);
        verify(redisTemplate).delete(TenantStatusKeys.expireKey(TENANT_ID));
    }

    @Test
    @DisplayName("sync：租户为空或缺少 ID 时跳过写入")
    void shouldSkipWhenTenantInvalid() {
        cache.sync(null);
        cache.sync(Tenant.create("acme", "ACME", null, null));

        verifyNoInteractions(redisTemplateProvider);
    }
}
