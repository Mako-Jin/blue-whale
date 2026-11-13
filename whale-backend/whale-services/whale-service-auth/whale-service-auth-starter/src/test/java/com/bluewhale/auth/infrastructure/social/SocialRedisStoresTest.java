package com.bluewhale.auth.infrastructure.social;

import com.bluewhale.auth.application.model.social.SocialLoginCodeRecord;
import com.bluewhale.auth.application.model.social.SocialStateRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三方登录 Redis 存储单元测试（权限中心扩展 M2）：state 存储与一次性登录码存储。
 *
 * <p>覆盖：写入带 TTL、JSON 序列化往返、一次性消费语义（并发冲突失败）、异常归一。</p>
 *
 * @author BlueWhale
 */
class SocialRedisStoresTest {

    private static final String STATE_KEY = "auth:social:state:state-1";
    private static final String CODE_KEY = "auth:social:login-code:code-1";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private RedisSocialStateStore stateStore;
    private RedisSocialLoginCodeStore codeStore;

    /**
     * 初始化被测对象并绑定 ValueOperations
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stateStore = new RedisSocialStateStore(redisTemplate, objectMapper);
        codeStore = new RedisSocialLoginCodeStore(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("state：保存带 TTL，读取按 JSON 还原载荷")
    void stateSaveWithTtlAndLoadRoundTrip() {
        SocialStateRecord record = new SocialStateRecord("dingtalk", 1L, null,
                "nonce", "/home", 1700000000000L);

        stateStore.save("state-1", record, 300L);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(STATE_KEY), jsonCaptor.capture(),
                eq(Duration.ofSeconds(300L)));
        assertThat(jsonCaptor.getValue()).contains("\"provider\":\"dingtalk\"");

        when(valueOperations.get(STATE_KEY)).thenReturn(jsonCaptor.getValue());
        Optional<SocialStateRecord> loaded = stateStore.load("state-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().provider()).isEqualTo("dingtalk");
        assertThat(loaded.get().tenantId()).isEqualTo(1L);
        assertThat(loaded.get().redirect()).isEqualTo("/home");
        assertThat(loaded.get().bindIntent()).isFalse();
    }

    @Test
    @DisplayName("state：一次性消费依赖 Redis delete 返回值")
    void stateConsumeIsAtomic() {
        when(redisTemplate.delete(STATE_KEY)).thenReturn(true);
        assertThat(stateStore.consume("state-1")).isTrue();

        when(redisTemplate.delete(STATE_KEY)).thenReturn(false);
        assertThat(stateStore.consume("state-1")).isFalse();
    }

    @Test
    @DisplayName("state：key 不存在返回空；损坏 JSON 抛异常")
    void stateLoadHandlesMissingAndCorrupt() {
        when(valueOperations.get(STATE_KEY)).thenReturn(null);
        assertThat(stateStore.load("state-1")).isEmpty();

        when(valueOperations.get(STATE_KEY)).thenReturn("not-json");
        assertThatThrownBy(() -> stateStore.load("state-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state 载荷反序列化失败");
    }

    @Test
    @DisplayName("登录码：保存带 TTL；get 后原子 delete 成功才消费")
    void loginCodeSaveAndConsume() throws Exception {
        SocialLoginCodeRecord record = new SocialLoginCodeRecord(2L, 1L, 1700000000000L);
        codeStore.save("code-1", record, 60L);
        verify(valueOperations).set(eq(CODE_KEY), anyString(), eq(Duration.ofSeconds(60L)));

        String json = objectMapper.writeValueAsString(record);
        when(valueOperations.get(CODE_KEY)).thenReturn(json);
        when(redisTemplate.delete(CODE_KEY)).thenReturn(true);

        Optional<SocialLoginCodeRecord> consumed = codeStore.consume("code-1");
        assertThat(consumed).isPresent();
        assertThat(consumed.get().userId()).isEqualTo(2L);

        when(redisTemplate.delete(CODE_KEY)).thenReturn(false);
        assertThat(codeStore.consume("code-1")).isEmpty();
    }

    @Test
    @DisplayName("登录码：缺失返回空；写入异常归一为 IllegalStateException")
    void loginCodeMissingAndWriteFailure() {
        when(valueOperations.get(CODE_KEY)).thenReturn(null);
        assertThat(codeStore.consume("code-1")).isEmpty();

        org.mockito.Mockito.doThrow(new RuntimeException("down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        assertThatThrownBy(() -> codeStore.save("code-1",
                new SocialLoginCodeRecord(2L, 1L, 1L), 60L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("登录码写入 Redis 失败");
    }
}
