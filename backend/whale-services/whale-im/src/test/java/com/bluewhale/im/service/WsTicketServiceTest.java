package com.bluewhale.im.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.vo.WsTicketVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WsTicketService} 单元测试：ticket 签发落盘、一次性消费、Redis 异常失败关闭。
 *
 * @author BlueWhale
 */
class WsTicketServiceTest {

    private static final long USER_ID = 10L;

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final WsTicketService service = new WsTicketService(redisTemplate, 60L);

    /**
     * 绑定 Redis 值操作桩
     */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("issue：ticket 以 whale:im:ticket: 前缀落盘并带 60 秒 TTL")
    void issueShouldStoreTicketWithTtl() {
        WsTicketVO ticket = service.issue(USER_ID);

        assertNotNull(ticket.getTicket());
        assertEquals(60L, ticket.getExpiresIn());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq(String.valueOf(USER_ID)), eq(Duration.ofSeconds(60)));
        assertTrue(keyCaptor.getValue().startsWith("whale:im:ticket:"));
        assertTrue(keyCaptor.getValue().endsWith(ticket.getTicket()));
    }

    @Test
    @DisplayName("issue：Redis 不可用时失败关闭，不签发 ticket")
    void issueShouldFailClosedWhenRedisDown() {
        doThrow(new RuntimeException("down")).when(valueOperations)
                .set(anyString(), anyString(), eq(Duration.ofSeconds(60)));

        assertThrows(BizException.class, () -> service.issue(USER_ID));
    }

    @Test
    @DisplayName("consume：原子取删返回绑定用户ID")
    void consumeShouldReturnUserId() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(String.valueOf(USER_ID));

        assertEquals(USER_ID, service.consume("abc"));
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(Collections.singletonList("whale:im:ticket:abc")));
    }

    @Test
    @DisplayName("consume：空 ticket 直接拒绝且不访问 Redis")
    void consumeShouldRejectBlankTicket() {
        assertNull(service.consume(" "));
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("consume：已使用或已过期的 ticket 返回 null")
    void consumeShouldReturnNullWhenMissing() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(null);

        assertNull(service.consume("used"));
    }

    @Test
    @DisplayName("consume：Redis 异常时失败关闭，返回 null")
    void consumeShouldFailClosedWhenRedisError() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new IllegalStateException("ERR unknown command"));

        assertNull(service.consume("abc"));
    }
}
