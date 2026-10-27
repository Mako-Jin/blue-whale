package com.bluewhale.auth.infrastructure.cache;

import com.bluewhale.auth.config.PermissionCacheProperties;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import com.bluewhale.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthPermissionCacheInvalidator} 单元测试：完整 Key 删除、广播回声过滤、失败登记与断连重对齐。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuthPermissionCacheInvalidatorTest {

    private static final long TENANT_ID = 7L;

    private static final String KEY_PREFIX = "whale:" + TENANT_ID + ":cache:auth:user:perm:";

    @Mock
    private ObjectProvider<RedissonClient> redissonProvider;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RTopic topic;

    @Mock
    private CacheConsistencyTemplate cacheTemplate;

    private PermissionCacheProperties properties;

    private AuthPermissionCacheInvalidator invalidator;

    /** 重启恢复测试用临时目录 */
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        properties = new PermissionCacheProperties();
        properties.setPendingPersistenceEnabled(false);
        invalidator = new AuthPermissionCacheInvalidator(redissonProvider, cacheTemplate, properties);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 断言批量删除的 Key 集合与期望一致
     *
     * @param captor 已捕获的 Key 集合参数
     * @param expected 期望 Key 集合
     */
    private static void assertKeys(ArgumentCaptor<Collection<String>> captor, Set<String> expected) {
        assertEquals(expected, new HashSet<>(captor.getValue()));
    }

    @Test
    @DisplayName("失效用户：按当前租户计算完整 Key 并去重批量删除，成功后无待重对齐")
    void shouldDeleteKeysForDistinctUsers() {
        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(true);

        invalidator.invalidateUsers(List.of(1L, 1L, 2L));

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(cacheTemplate).tryEvictKeys(captor.capture());
        assertKeys(captor, Set.of(KEY_PREFIX + "1", KEY_PREFIX + "2"));
        assertEquals(0, invalidator.pendingCount());
    }

    @Test
    @DisplayName("跨租户失效：按用户所属租户构造 Key，不受当前 TenantContext 影响")
    void shouldDeleteKeysAcrossTenants() {
        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(true);

        invalidator.invalidateUsersByTenant(List.of(
                new AuthPermissionCacheInvalidator.UserKey(11L, 1L),
                new AuthPermissionCacheInvalidator.UserKey(12L, 2L),
                new AuthPermissionCacheInvalidator.UserKey(12L, 2L),
                new AuthPermissionCacheInvalidator.UserKey(null, 3L)));

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(cacheTemplate).tryEvictKeys(captor.capture());
        assertKeys(captor, Set.of(
                "whale:11:cache:auth:user:perm:1",
                "whale:12:cache:auth:user:perm:2"));
    }

    @Test
    @DisplayName("跨租户失效：空集合或坐标非法时不触发删除")
    void shouldSkipCrossTenantEvictWhenNoValidKeys() {
        invalidator.invalidateUsersByTenant(null);
        invalidator.invalidateUsersByTenant(List.of());
        invalidator.invalidateUsersByTenant(List.of(new AuthPermissionCacheInvalidator.UserKey(1L, null)));

        verify(cacheTemplate, never()).tryEvictKeys(anyCollection());
    }

    @Test
    @DisplayName("删除异常：登记待重对齐，Redis 恢复后由 flushPending 清除")
    void shouldRetryPendingKeysAfterRedisRecovery() {
        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(false);

        invalidator.invalidateUsers(List.of(3L));

        assertEquals(1, invalidator.pendingCount());

        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(true);

        assertEquals(1, invalidator.flushPending());
        assertEquals(0, invalidator.pendingCount());
    }

    @Test
    @DisplayName("广播：发布完整 Key 消息，远端消息触发本地删除，回声消息被忽略")
    @SuppressWarnings("unchecked")
    void shouldBroadcastKeysAndApplyRemoteMessageWhileIgnoringEcho() {
        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(true);
        when(redissonProvider.getIfAvailable()).thenReturn(redissonClient);
        when(redissonClient.getTopic(AuthPermissionCacheInvalidator.TOPIC_NAME)).thenReturn(topic);
        when(topic.addListener(eq(String.class), any())).thenReturn(1);

        invalidator.subscribe();
        invalidator.invalidateUsers(List.of(5L));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(topic).publish(payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertTrue(payload.startsWith("v1|KEYS|"), "消息应包含协议版本与作用域");
        assertTrue(payload.endsWith("|" + KEY_PREFIX + "5"), "消息应包含完整缓存 Key");

        ArgumentCaptor<MessageListener<String>> listenerCaptor =
                ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), listenerCaptor.capture());
        MessageListener<String> listener = listenerCaptor.getValue();

        String remoteKey = "whale:9:cache:auth:user:perm:6";
        listener.onMessage(AuthPermissionCacheInvalidator.TOPIC_NAME, "v1|KEYS|remote|" + remoteKey);
        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(cacheTemplate, times(2)).tryEvictKeys(keysCaptor.capture());
        assertEquals(Set.of(remoteKey), new HashSet<>(keysCaptor.getAllValues().get(1)));

        // 回声消息：本实例已删除一次，不应重复删除
        listener.onMessage(AuthPermissionCacheInvalidator.TOPIC_NAME, payload);
        verify(cacheTemplate, times(2)).tryEvictKeys(anyCollection());
    }

    @Test
    @DisplayName("广播关闭：不订阅主题，但仍执行本地删除")
    void shouldSkipBroadcastWhenDisabled() {
        properties.setBroadcastEnabled(false);
        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(true);

        invalidator.subscribe();
        invalidator.invalidateUsers(List.of(7L));

        verify(redissonClient, never()).getTopic(anyString());
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(cacheTemplate).tryEvictKeys(captor.capture());
        assertKeys(captor, Set.of(KEY_PREFIX + "7"));
    }

    @Test
    @DisplayName("非法广播消息：忽略且不触发删除")
    void shouldIgnoreMalformedMessage() {
        invalidator.handleMessage("garbage");
        invalidator.handleMessage("v1|USER|remote|1");
        invalidator.handleMessage("v1|KEYS|remote|evil:key");
        invalidator.handleMessage("v1|KEYS|remote|");

        verify(cacheTemplate, never()).tryEvictKeys(anyCollection());
    }

    @Test
    @DisplayName("待处理上限：超过 pendingLimit 后不再登记，避免断连期间内存膨胀")
    void shouldCapPendingKeys() {
        properties.setPendingLimit(1);
        when(cacheTemplate.tryEvictKeys(anyCollection())).thenReturn(false);

        invalidator.invalidateUsers(List.of(8L, 9L));

        assertEquals(1, invalidator.pendingCount());
    }

    @Test
    @DisplayName("重启恢复：待重对齐 Key 落盘并在重启后自动读回，清理成功后文件为空")
    void shouldRestorePendingKeysAfterRestart() throws Exception {
        Path pendingFile = tempDir.resolve("pending.keys");
        PermissionCacheProperties shared = new PermissionCacheProperties();
        shared.setBroadcastEnabled(false);
        shared.setPendingFile(pendingFile.toString());

        CacheConsistencyTemplate unavailable = mock(CacheConsistencyTemplate.class);
        when(unavailable.tryEvictKeys(anyCollection())).thenReturn(false);
        AuthPermissionCacheInvalidator beforeRestart =
                new AuthPermissionCacheInvalidator(redissonProvider, unavailable, shared);
        beforeRestart.subscribe();
        beforeRestart.invalidateUsers(List.of(21L));

        assertEquals(1, beforeRestart.pendingCount());
        assertTrue(Files.readString(pendingFile).contains(KEY_PREFIX + "21"));

        CacheConsistencyTemplate recovered = mock(CacheConsistencyTemplate.class);
        when(recovered.tryEvictKeys(anyCollection())).thenReturn(true);
        AuthPermissionCacheInvalidator afterRestart =
                new AuthPermissionCacheInvalidator(redissonProvider, recovered, shared);
        afterRestart.subscribe();

        assertEquals(1, afterRestart.pendingCount());
        assertEquals(1, afterRestart.flushPending());
        assertEquals(0, afterRestart.pendingCount());
        assertTrue(Files.readString(pendingFile).isBlank());
    }

    @Test
    @DisplayName("退订：停机时移除主题监听")
    void shouldRemoveListenersOnUnsubscribe() {
        when(redissonProvider.getIfAvailable()).thenReturn(redissonClient);
        when(redissonClient.getTopic(AuthPermissionCacheInvalidator.TOPIC_NAME)).thenReturn(topic);
        when(topic.addListener(eq(String.class), any())).thenReturn(1);

        invalidator.subscribe();
        invalidator.unsubscribe();

        verify(topic).removeAllListeners();
    }
}
