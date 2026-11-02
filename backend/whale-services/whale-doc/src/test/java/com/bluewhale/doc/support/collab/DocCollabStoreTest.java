package com.bluewhale.doc.support.collab;

import com.bluewhale.doc.config.DocProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocCollabStore} Redis 交互断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocCollabStoreTest {

    private static final Long TENANT = 3L;

    private static final Long PAGE = 9L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ListOperations listOps;

    @Mock
    private HashOperations hashOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DocProperties properties = new DocProperties();

    private DocCollabStore store;

    @BeforeEach
    void setUp() {
        properties.setCollabTicketTtlSeconds(60);
        properties.setCollabUpdateKeep(2000);
        properties.setCollabUpdateTtlSeconds(7200);
        properties.setCollabActiveTtlSeconds(180);
        properties.setCollabLeaderTtlSeconds(30);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        store = new DocCollabStore(redisTemplate, objectMapper, properties);
    }

    @Test
    void instanceIdShouldBeStableAndDistinct() {
        assertThat(store.instanceId()).isNotBlank().isEqualTo(store.instanceId());
        assertThat(new DocCollabStore(redisTemplate, objectMapper, properties).instanceId())
                .isNotEqualTo(store.instanceId());
    }

    @Test
    void saveTicketShouldStoreJsonWithTtl() {
        DocWsTicket ticket = new DocWsTicket(TENANT, 5L, PAGE, "alice");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        store.saveTicket("t-1", ticket);

        verify(valueOps).set(eq("whale:doc:collab:ticket:t-1"), json.capture(), eq(Duration.ofSeconds(60)));
        assertThat(json.getValue()).contains("\"tenantId\":3").contains("\"username\":\"alice\"");
    }

    @Test
    void consumeTicketShouldReturnParsedTicket() {
        DocWsTicket ticket = new DocWsTicket(TENANT, 5L, PAGE, "alice");
        doReturn(objectMapper.writeValueAsString(ticket)).when(redisTemplate)
                .execute(any(RedisScript.class), anyList());

        assertThat(store.consumeTicket("t-1")).isEqualTo(ticket);
    }

    @Test
    void consumeTicketShouldReturnNullWhenMissing() {
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList());
        assertThat(store.consumeTicket("t-1")).isNull();

        doReturn("  ").when(redisTemplate).execute(any(RedisScript.class), anyList());
        assertThat(store.consumeTicket("t-1")).isNull();
    }

    @Test
    void loadUpdatesShouldReturnEmptyWhenRedisMissing() {
        when(listOps.range("whale:doc:collab:updates:3:9", 0, -1)).thenReturn(null);
        assertThat(store.loadUpdates(TENANT, PAGE)).isEmpty();
    }

    @Test
    void loadUpdatesShouldReturnStoredValues() {
        when(listOps.range("whale:doc:collab:updates:3:9", 0, -1)).thenReturn(List.of("u1", "u2"));
        assertThat(store.loadUpdates(TENANT, PAGE)).containsExactly("u1", "u2");
    }

    @Test
    void appendUpdateShouldPushTrimAndExpire() {
        store.appendUpdate(TENANT, PAGE, "u1");

        verify(listOps).rightPush("whale:doc:collab:updates:3:9", "u1");
        verify(listOps).trim("whale:doc:collab:updates:3:9", -2000, -1);
        verify(redisTemplate).expire("whale:doc:collab:updates:3:9", 7200, TimeUnit.SECONDS);
    }

    @Test
    void isActiveShouldHandleNullAndPositiveSize() {
        when(hashOps.size("whale:doc:collab:presence:3:9")).thenReturn(null);
        assertThat(store.isActive(TENANT, PAGE)).isFalse();

        when(hashOps.size("whale:doc:collab:presence:3:9")).thenReturn(0L);
        assertThat(store.isActive(TENANT, PAGE)).isFalse();

        when(hashOps.size("whale:doc:collab:presence:3:9")).thenReturn(2L);
        assertThat(store.isActive(TENANT, PAGE)).isTrue();
    }

    @Test
    void touchPresenceShouldRefreshLastSeenAndExpire() {
        DocPresenceUser user = presence("c1", 5L, "alice", "#2563eb", 0L);

        store.touchPresence(TENANT, PAGE, user);

        assertThat(user.getLastSeen()).isPositive();
        verify(hashOps).put(eq("whale:doc:collab:presence:3:9"), eq("c1"), anyString());
        verify(redisTemplate).expire("whale:doc:collab:presence:3:9", 180, TimeUnit.SECONDS);
    }

    @Test
    void removePresenceShouldDeleteHashField() {
        store.removePresence(TENANT, PAGE, "c1");

        verify(hashOps).delete("whale:doc:collab:presence:3:9", "c1");
    }

    @Test
    void presenceUsersShouldSkipInvalidAndStaleEntriesAndSortByName() {
        long now = System.currentTimeMillis();
        Map<Object, Object> entries = new HashMap<>();
        entries.put("c2", objectMapper.writeValueAsString(presence("c2", 6L, "zoe", "#123456", now)));
        entries.put("c1", objectMapper.writeValueAsString(presence("c1", 5L, "amy", "#654321", now)));
        entries.put("bad", "not-json");
        entries.put("stale", objectMapper.writeValueAsString(presence("stale", 7L, "old", "#000000", now - 10_000_000L)));
        entries.put("null", null);
        when(hashOps.entries("whale:doc:collab:presence:3:9")).thenReturn(entries);

        List<DocPresenceUser> users = store.presenceUsers(TENANT, PAGE);

        assertThat(users).extracting(DocPresenceUser::getName).containsExactly("amy", "zoe");
        verify(hashOps).delete("whale:doc:collab:presence:3:9", "stale");
    }

    @Test
    void presenceUsersShouldTreatNullNameAsEmptyWhenSorting() {
        long now = System.currentTimeMillis();
        Map<Object, Object> entries = new HashMap<>();
        entries.put("c1", objectMapper.writeValueAsString(presence("c1", 5L, null, "#654321", now)));
        entries.put("c2", objectMapper.writeValueAsString(presence("c2", 6L, "bob", "#123456", now)));
        when(hashOps.entries("whale:doc:collab:presence:3:9")).thenReturn(entries);

        List<DocPresenceUser> users = store.presenceUsers(TENANT, PAGE);

        assertThat(users).hasSize(2);
        assertThat(users.get(0).getClientId()).isEqualTo("c1");
    }

    @Test
    void claimLeaderShouldInterpretScriptResult() {
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        assertThat(store.claimLeader(TENANT, PAGE, "c1")).isFalse();

        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        assertThat(store.claimLeader(TENANT, PAGE, "c1")).isFalse();

        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        assertThat(store.claimLeader(TENANT, PAGE, "c1")).isTrue();
    }

    @Test
    void isLeaderShouldCompareOwner() {
        when(valueOps.get("whale:doc:collab:leader:3:9")).thenReturn("c1");

        assertThat(store.isLeader(TENANT, PAGE, "c1")).isTrue();
        assertThat(store.isLeader(TENANT, PAGE, "c2")).isFalse();
    }

    @Test
    void releaseLeaderShouldRunScript() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        store.releaseLeader(TENANT, PAGE, "c1");

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("c1"));
    }

    @Test
    void publishShouldSendEnvelopeToChannel() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        store.publish(TENANT, PAGE, "{\"type\":\"update\"}", "c1", "c2");

        verify(redisTemplate).convertAndSend(eq("whale:doc:collab:channel:3:9"), body.capture());
        assertThat(body.getValue()).contains("\"origin\":\"" + store.instanceId() + "\"")
                .contains("\"excludeClientId\":\"c1\"")
                .contains("\"targetClientId\":\"c2\"")
                .contains("\"frame\"");
    }

    @Test
    void publishShouldSwallowRedisFailure() {
        doThrow(new RuntimeException("redis down")).when(redisTemplate)
                .convertAndSend(anyString(), anyString());

        assertThatCode(() -> store.publish(TENANT, PAGE, "{}", null, null)).doesNotThrowAnyException();
    }

    /**
     * 构造在线用户。
     *
     * @param clientId 客户端ID
     * @param userId   用户ID
     * @param name     用户名
     * @param color    颜色
     * @param lastSeen 最近心跳
     * @return 在线用户
     */
    private DocPresenceUser presence(String clientId, Long userId, String name, String color, long lastSeen) {
        DocPresenceUser user = new DocPresenceUser();
        user.setClientId(clientId);
        user.setUserId(userId);
        user.setName(name);
        user.setColor(color);
        user.setLastSeen(lastSeen);
        return user;
    }
}
