package com.bluewhale.doc.support.collab;

import com.bluewhale.doc.config.DocProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 实时协作 Redis 存储（M2）。
 *
 * <p>承载 WS ticket、页面更新日志、在线 presence、单页 leader 锁与跨实例广播。
 * 更新日志使用 List 追加并按 {@code collab-update-keep} 截断；presence 使用 Hash，
 * key TTL 由心跳续期；leader 使用 {@code SET NX EX} + 比较删除 Lua 脚本保证全局唯一。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocCollabStore {

    /** Redis key 前缀：一次性 WS ticket */
    private static final String TICKET_PREFIX = "whale:doc:collab:ticket:";

    /** Redis key 前缀：页面 Yjs 更新日志 */
    private static final String UPDATES_PREFIX = "whale:doc:collab:updates:";

    /** Redis key 前缀：页面在线 presence */
    private static final String PRESENCE_PREFIX = "whale:doc:collab:presence:";

    /** Redis key 前缀：页面 leader 锁 */
    private static final String LEADER_PREFIX = "whale:doc:collab:leader:";

    /** Redis Pub/Sub 频道前缀：跨实例协作帧广播 */
    private static final String CHANNEL_PREFIX = "whale:doc:collab:channel:";

    /** leader 抢占/续期脚本：空则设置，同 owner 续期，否则失败 */
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('get', KEYS[1])
            if value == false then
                redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
                return 1
            end
            if value == ARGV[1] then
                redis.call('expire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """, Long.class);

    /** ticket 一次性消费脚本：兼容无 GETDEL 的低版本 Redis */
    private static final DefaultRedisScript<String> CONSUME_TICKET_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('get', KEYS[1])
            if value == false then
                return nil
            end
            redis.call('del', KEYS[1])
            return value
            """, String.class);

    /** leader 释放脚本：仅当 owner 匹配时删除 */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final DocProperties docProperties;

    /** 当前服务实例标识，用于忽略自身发布的 Pub/Sub 消息 */
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * 当前服务实例标识。
     *
     * @return 实例ID
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * 保存一次性 WS ticket。
     *
     * @param ticket 票据
     * @param value  票据载荷
     */
    public void saveTicket(String ticket, DocWsTicket value) {
        redisTemplate.opsForValue().set(ticketKey(ticket), toJson(value),
                Duration.ofSeconds(docProperties.getCollabTicketTtlSeconds()));
    }

    /**
     * 原子读取并删除 WS ticket。
     *
     * @param ticket 票据
     * @return 票据载荷；不存在或已消费返回 {@code null}
     */
    public DocWsTicket consumeTicket(String ticket) {
        String json = redisTemplate.execute(CONSUME_TICKET_SCRIPT, List.of(ticketKey(ticket)));
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, DocWsTicket.class);
    }

    /**
     * 读取页面协作更新日志。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return base64 Yjs 更新列表
     */
    public List<String> loadUpdates(Long tenantId, Long pageId) {
        List<String> values = redisTemplate.opsForList().range(updatesKey(tenantId, pageId), 0, -1);
        return values == null ? List.of() : values;
    }

    /**
     * 追加一条 Yjs 更新并截断历史。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param update   base64 编码的 Yjs 更新
     */
    public void appendUpdate(Long tenantId, Long pageId, String update) {
        String key = updatesKey(tenantId, pageId);
        redisTemplate.opsForList().rightPush(key, update);
        long keep = docProperties.getCollabUpdateKeep();
        redisTemplate.opsForList().trim(key, -keep, -1);
        redisTemplate.expire(key, docProperties.getCollabUpdateTtlSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 判断页面是否存在活跃协作会话。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 存在返回 {@code true}
     */
    public boolean isActive(Long tenantId, Long pageId) {
        Long size = redisTemplate.opsForHash().size(presenceKey(tenantId, pageId));
        return size != null && size > 0;
    }

    /**
     * 写入/刷新在线用户 presence。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param user     在线用户
     */
    public void touchPresence(Long tenantId, Long pageId, DocPresenceUser user) {
        String key = presenceKey(tenantId, pageId);
        user.setLastSeen(System.currentTimeMillis());
        redisTemplate.opsForHash().put(key, user.getClientId(), toJson(user));
        redisTemplate.expire(key, docProperties.getCollabActiveTtlSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 移除离线用户 presence。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param clientId 客户端ID
     */
    public void removePresence(Long tenantId, Long pageId, String clientId) {
        redisTemplate.opsForHash().delete(presenceKey(tenantId, pageId), clientId);
    }

    /**
     * 读取在线用户列表并清理过期心跳。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 在线用户（按名称排序）
     */
    public List<DocPresenceUser> presenceUsers(Long tenantId, Long pageId) {
        String key = presenceKey(tenantId, pageId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        List<DocPresenceUser> users = new ArrayList<>(entries.size());
        long staleBefore = System.currentTimeMillis() - docProperties.getCollabActiveTtlSeconds() * 1000L;
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            DocPresenceUser user = parsePresence(entry.getValue());
            if (user == null) {
                continue;
            }
            if (user.getLastSeen() < staleBefore) {
                redisTemplate.opsForHash().delete(key, entry.getKey());
                continue;
            }
            users.add(user);
        }
        users.sort(Comparator.comparing(user -> user.getName() == null ? "" : user.getName()));
        return users;
    }

    /**
     * 抢占或续期页面 leader 锁。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param clientId 客户端ID
     * @return 成为 leader 返回 {@code true}
     */
    public boolean claimLeader(Long tenantId, Long pageId, String clientId) {
        Long result = redisTemplate.execute(CLAIM_SCRIPT, List.of(leaderKey(tenantId, pageId)),
                clientId, String.valueOf(docProperties.getCollabLeaderTtlSeconds()));
        return result != null && result > 0;
    }

    /**
     * 判断当前客户端是否为 leader。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param clientId 客户端ID
     * @return 是 leader 返回 {@code true}
     */
    public boolean isLeader(Long tenantId, Long pageId, String clientId) {
        String owner = redisTemplate.opsForValue().get(leaderKey(tenantId, pageId));
        return clientId.equals(owner);
    }

    /**
     * 释放 leader 锁（仅 owner 匹配时）。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param clientId 客户端ID
     */
    public void releaseLeader(Long tenantId, Long pageId, String clientId) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(leaderKey(tenantId, pageId)), clientId);
    }

    /**
     * 发布跨实例协作帧。
     *
     * @param tenantId        租户ID
     * @param pageId          页面ID
     * @param frame           帧 JSON
     * @param excludeClientId 排除的客户端ID，可为 {@code null}
     * @param targetClientId  仅投递的客户端ID，可为 {@code null} 表示广播
     */
    public void publish(Long tenantId, Long pageId, String frame, String excludeClientId, String targetClientId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("tenantId", tenantId);
        envelope.put("pageId", pageId);
        envelope.put("origin", instanceId);
        envelope.put("excludeClientId", excludeClientId);
        envelope.put("targetClientId", targetClientId);
        envelope.put("frame", frame);
        try {
            redisTemplate.convertAndSend(channel(tenantId, pageId), toJson(envelope));
        } catch (RuntimeException ex) {
            log.warn("协作帧跨实例广播失败: pageId={}, reason={}", pageId, ex.getMessage());
        }
    }

    /**
     * 生成 ticket Redis key。
     *
     * @param ticket 票据
     * @return key
     */
    private String ticketKey(String ticket) {
        return TICKET_PREFIX + ticket;
    }

    /**
     * 生成更新日志 Redis key。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return key
     */
    private String updatesKey(Long tenantId, Long pageId) {
        return UPDATES_PREFIX + tenantId + ":" + pageId;
    }

    /**
     * 生成 presence Redis key。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return key
     */
    private String presenceKey(Long tenantId, Long pageId) {
        return PRESENCE_PREFIX + tenantId + ":" + pageId;
    }

    /**
     * 生成 leader Redis key。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return key
     */
    private String leaderKey(Long tenantId, Long pageId) {
        return LEADER_PREFIX + tenantId + ":" + pageId;
    }

    /**
     * 生成 Pub/Sub 频道名。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 频道名
     */
    private String channel(Long tenantId, Long pageId) {
        return CHANNEL_PREFIX + tenantId + ":" + pageId;
    }

    /**
     * 解析 presence JSON。
     *
     * @param value Redis Hash 值
     * @return 在线用户；解析失败返回 {@code null}
     */
    private DocPresenceUser parsePresence(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), DocPresenceUser.class);
        } catch (RuntimeException ex) {
            log.warn("协作 presence 数据解析失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 对象序列化为 JSON。
     *
     * @param value 对象
     * @return JSON 文本
     */
    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
