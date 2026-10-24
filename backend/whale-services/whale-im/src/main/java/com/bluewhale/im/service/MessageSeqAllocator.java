package com.bluewhale.im.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 会话消息序号分配器。
 *
 * <p>正常路径：Redis 计数器 {@code INCR}（键前缀 {@code whale:im:seq:} 拼会话ID），首次使用以库内 {@code lastSeq}
 * 作为基数 {@code SETNX} 初始化，多实例并发下只会有一个实例完成初始化。</p>
 *
 * <p>降级路径：Redis 不可用时回落到「库内会话 lastSeq + 1」；调用方插入冲突时重新分配，
 * 由 {@code (conversation_id, seq)} 唯一索引兜底，绝不允许重复序号落库。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSeqAllocator {

    /** 序号键前缀（REQ-ARCH-004 命名：whale:域:用途:标识） */
    private static final String SEQ_KEY_PREFIX = "whale:im:seq:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 分配下一个消息序号。
     *
     * @param conversationId 会话ID
     * @param databaseLastSeq 库内当前最大序号（作为 Redis 冷启动基数与降级回落值）
     * @return 单调递增序号
     */
    public long next(long conversationId, long databaseLastSeq) {
        String key = SEQ_KEY_PREFIX + conversationId;
        try {
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(databaseLastSeq));
            Long seq = redisTemplate.opsForValue().increment(key);
            if (seq != null && seq > 0L) {
                return seq;
            }
            log.warn("Redis INCR 未返回有效序号，降级为库内序号: conversationId={}", conversationId);
        } catch (RuntimeException e) {
            log.warn("Redis 不可用，消息序号降级为库内分配: conversationId={}", conversationId, e);
        }
        return databaseLastSeq + 1L;
    }
}
