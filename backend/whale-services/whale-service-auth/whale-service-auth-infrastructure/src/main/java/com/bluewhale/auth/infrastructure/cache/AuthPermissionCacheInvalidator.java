package com.bluewhale.auth.infrastructure.cache;

import com.bluewhale.auth.config.PermissionCacheProperties;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import com.bluewhale.common.tenant.TenantKeys;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限缓存跨实例失效器（AUTH-20 / REQ-DIST-003）。
 *
 * <p>失效路径：按当前租户计算受影响用户的完整缓存 Key 并批量删除；删除成功后再经
 * Redisson RTopic 广播这些 Key，其余实例收到后执行同样的删除，用于「实例 A 改了权限、
 * 实例 B 仍可能持有旧值」的场景（共享 Redis 删除为主，广播为跨实例兜底与可观测）。</p>
 *
 * <p>Redis 断连重对齐：删除失败时以<b>完整 Key</b>（而非用户 ID）登记待处理集合，
 * 由 {@link AuthPermissionCacheReconcileJob} 定时重试。这样异步重试不依赖
 * {@code TenantContext}，Redis 恢复后即可完成清理，不必等待 TTL 过期或下一次权限变更。</p>
 *
 * <p>广播消息为纯文本 {@code v1|KEYS|{instanceId}|{key1,key2,...}}，实例 ID 用于回声过滤；
 * 接收方仅接受 {@code whale:} 前缀的 Key，过滤异常消息。Redisson 不可用时不广播，
 * 但共享 Redis 删除与断连重对齐仍然生效。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthPermissionCacheInvalidator {

    /** 跨实例失效广播主题 */
    public static final String TOPIC_NAME = "auth:permission-cache:invalidate";

    /** 消息协议版本 */
    private static final String MESSAGE_VERSION = "v1";

    /** 消息作用域：完整缓存 Key 维度 */
    private static final String SCOPE_KEYS = "KEYS";

    /** 消息字段分隔符 */
    private static final String MESSAGE_DELIMITER = "|";

    /** Redis Key 列分隔符 */
    private static final String KEY_LIST_DELIMITER = ",";

    /** 仅允许处理的 Key 前缀，防御异常广播消息 */
    private static final String KEY_PREFIX = TenantKeys.PREFIX + TenantKeys.SEPARATOR;

    /** 单条广播消息最多携带的 Key 数，避免消息体过大 */
    private static final int MAX_KEYS_PER_MESSAGE = 100;

    private final ObjectProvider<RedissonClient> redissonProvider;

    private final CacheConsistencyTemplate cacheTemplate;

    private final PermissionCacheProperties properties;

    /** 待重对齐的完整缓存 Key（Redis 删除失败时登记） */
    private final Set<String> pendingKeys = ConcurrentHashMap.newKeySet();

    /**
     * 用户缓存 Key 坐标（租户 + 用户）。
     *
     * <p>权限点为平台共享数据，一次权限点变更会命中多个租户的角色与用户，需按用户实际所属租户
     * 逐个构造完整 Key；仅凭 {@code TenantContext} 无法跨租户定位。</p>
     *
     * @param tenantId 用户所属租户 ID
     * @param userId 用户 ID
     */
    public record UserKey(Long tenantId, Long userId) {
    }

    /** 本实例标识，用于过滤广播回声 */
    private final String instanceId = UUID.randomUUID().toString().replace("-", "");

    private volatile RTopic topic;

    /**
     * 启动时订阅跨实例失效主题；Redisson 不可用或关闭广播时仅保留共享 Redis 删除
     */
    @PostConstruct
    void subscribe() {
        if (!properties.isBroadcastEnabled()) {
            log.info("权限缓存跨实例失效广播已关闭，仅依赖共享 Redis 删除与断连重对齐");
            return;
        }
        RedissonClient client = currentClient();
        if (client == null) {
            log.warn("Redisson 不可用，权限缓存跨实例广播降级为共享 Redis 删除 + 断连重对齐");
            return;
        }
        try {
            RTopic rt = client.getTopic(TOPIC_NAME);
            rt.addListener(String.class, (channel, message) -> handleMessage(message));
            topic = rt;
            log.info("权限缓存跨实例失效广播已订阅: topic={}, instance={}", TOPIC_NAME, instanceId);
        } catch (Exception ex) {
            log.warn("权限缓存失效广播订阅失败，降级为共享 Redis 删除: {}", ex.getMessage());
        }
    }

    /**
     * 停机时移除广播监听，避免容器销毁阶段残留订阅
     */
    @PreDestroy
    void unsubscribe() {
        RTopic rt = topic;
        topic = null;
        if (rt == null) {
            return;
        }
        try {
            rt.removeAllListeners();
        } catch (Exception ex) {
            log.debug("权限缓存失效广播退订失败: {}", ex.getMessage());
        }
    }

    /**
     * 跨实例失效一批用户的权限缓存：本地批量删除完整 Key，失败的登记待重对齐，随后广播 Key。
     *
     * @param userIds 受影响用户 ID 集合；为空或全为非法 ID 时不执行
     */
    public void invalidateUsers(Collection<Long> userIds) {
        Set<Long> distinctUserIds = distinct(userIds);
        if (distinctUserIds.isEmpty()) {
            return;
        }
        Set<String> keys = new LinkedHashSet<>(distinctUserIds.size());
        for (Long userId : distinctUserIds) {
            keys.add(TenantKeys.cacheKey(PermissionCache.PERMISSION_CACHE_NAME, String.valueOf(userId)));
        }
        evictLocally(keys);
        publish(keys);
    }

    /**
     * 跨租户失效权限缓存（权限点更新/删除时，受影响用户可能分布在不同租户）。
     *
     * <p>与 {@link #invalidateUsers(Collection)} 的差异：Key 的租户段取用户实际所属租户，
     * 不依赖当前 {@code TenantContext}；其余删除、广播与断连重对齐语义一致。</p>
     *
     * @param userKeys 受影响用户的租户 + 用户坐标；为空或全部非法时不执行
     */
    public void invalidateUsersByTenant(Collection<UserKey> userKeys) {
        if (userKeys == null || userKeys.isEmpty()) {
            return;
        }
        Set<String> keys = new LinkedHashSet<>(userKeys.size());
        for (UserKey userKey : userKeys) {
            if (userKey == null || userKey.userId() == null || userKey.tenantId() == null) {
                continue;
            }
            keys.add(TenantKeys.cacheKey(userKey.tenantId(), PermissionCache.PERMISSION_CACHE_NAME,
                    String.valueOf(userKey.userId())));
        }
        if (keys.isEmpty()) {
            return;
        }
        evictLocally(keys);
        publish(keys);
    }

    /**
     * 重试待对齐的缓存删除（Redis 恢复后由定时任务调用）。
     *
     * @return 本次成功清除的 Key 数
     */
    public int flushPending() {
        if (pendingKeys.isEmpty()) {
            return 0;
        }
        int batchSize = Math.max(1, properties.getReconcileBatchSize());
        List<String> batch = pendingKeys.stream().limit(batchSize).toList();
        if (cacheTemplate.tryEvictKeys(batch)) {
            pendingKeys.removeAll(batch);
            return batch.size();
        }
        log.warn("权限缓存重对齐失败，保留待处理: 本次={}, 剩余={}", batch.size(), pendingKeys.size());
        return 0;
    }

    /**
     * 当前待重对齐的 Key 数（供监控与测试断言）
     *
     * @return 待处理 Key 数
     */
    public int pendingCount() {
        return pendingKeys.size();
    }

    /**
     * 处理广播消息：过滤回声与本实例消息，校验协议后清理远端实例上报的缓存 Key。
     *
     * @param message 广播消息
     */
    void handleMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }
        String[] parts = message.split("\\" + MESSAGE_DELIMITER, -1);
        if (parts.length != 4 || !MESSAGE_VERSION.equals(parts[0]) || !SCOPE_KEYS.equals(parts[1])) {
            log.warn("忽略非法权限缓存失效消息: length={}", message.length());
            return;
        }
        if (instanceId.equals(parts[2])) {
            return;
        }
        Set<String> keys = parseKeys(parts[3]);
        if (keys.isEmpty()) {
            return;
        }
        log.info("收到跨实例权限缓存失效广播: source={}, keys={}", parts[2], keys.size());
        evictLocally(keys);
    }

    /**
     * 本地批量删除缓存 Key，失败时登记待重对齐
     *
     * @param keys 完整缓存 Key 集合
     */
    private void evictLocally(Set<String> keys) {
        if (!cacheTemplate.tryEvictKeys(keys)) {
            markPending(keys);
            log.warn("权限缓存删除失败，登记待重对齐: keys={}", keys.size());
        }
    }

    /**
     * 登记待重对齐 Key，超过上限时丢弃并告警，防止长时间断连导致内存膨胀
     *
     * @param keys 完整缓存 Key 集合
     */
    private void markPending(Set<String> keys) {
        int limit = Math.max(1, properties.getPendingLimit());
        for (String key : keys) {
            if (!pendingKeys.contains(key) && pendingKeys.size() >= limit) {
                log.warn("权限缓存重对齐待处理已达上限 {}，丢弃 key={}", limit, key);
                continue;
            }
            pendingKeys.add(key);
        }
    }

    /**
     * 广播缓存 Key 失效消息；广播失败不阻断本地删除（共享 Redis 已清理，断连场景由重对齐兜底）
     *
     * @param keys 完整缓存 Key 集合
     */
    private void publish(Set<String> keys) {
        if (!properties.isBroadcastEnabled()) {
            return;
        }
        RTopic rt = topic;
        if (rt == null) {
            log.debug("权限缓存失效广播未就绪，跳过广播: keys={}", keys.size());
            return;
        }
        List<String> all = new ArrayList<>(keys);
        for (int start = 0; start < all.size(); start += MAX_KEYS_PER_MESSAGE) {
            int end = Math.min(start + MAX_KEYS_PER_MESSAGE, all.size());
            String joined = String.join(KEY_LIST_DELIMITER, all.subList(start, end));
            String payload = MESSAGE_VERSION + MESSAGE_DELIMITER + SCOPE_KEYS + MESSAGE_DELIMITER
                    + instanceId + MESSAGE_DELIMITER + joined;
            try {
                rt.publish(payload);
            } catch (Exception ex) {
                log.warn("权限缓存失效广播失败，依赖共享 Redis 删除与断连重对齐: {}", ex.getMessage());
                return;
            }
        }
    }

    /**
     * 去重并过滤非法用户 ID
     *
     * @param userIds 原始用户 ID 集合
     * @return 去重后的合法用户 ID 集合
     */
    private Set<Long> distinct(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                result.add(userId);
            }
        }
        return result;
    }

    /**
     * 解析逗号分隔的完整 Key 列表，仅保留 {@code whale:} 前缀的 Key
     *
     * @param raw 原始 Key 串
     * @return 合法 Key 集合
     */
    private Set<String> parseKeys(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split(KEY_LIST_DELIMITER)) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String key = token.trim();
            if (!key.startsWith(KEY_PREFIX)) {
                log.warn("忽略非法缓存 Key: {}", key);
                continue;
            }
            result.add(key);
        }
        return result;
    }

    /**
     * 获取 Redisson 客户端；未配置或初始化失败时返回 {@code null}
     *
     * @return Redisson 客户端或 {@code null}
     */
    private RedissonClient currentClient() {
        try {
            return redissonProvider.getIfAvailable();
        } catch (Exception ex) {
            log.debug("Redisson 客户端不可用: {}", ex.getMessage());
            return null;
        }
    }
}
