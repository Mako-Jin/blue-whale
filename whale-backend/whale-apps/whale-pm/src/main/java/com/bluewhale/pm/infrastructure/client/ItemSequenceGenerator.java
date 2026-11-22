package com.bluewhale.pm.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 工作项编号生成器：Redis 原子自增 {@code whale:pm:itemseq:<projectId>}。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemSequenceGenerator {

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "whale:pm:itemseq:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成项目内下一个工作项序号。
     *
     * @param projectId 项目ID
     * @return 序号（从 1 开始）
     */
    public long next(Long projectId) {
        Long value = redisTemplate.opsForValue().increment(KEY_PREFIX + projectId);
        if (value == null) {
            log.error("工作项序号生成失败: projectId={}", projectId);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.pm.infrastructure.failed-generate-work-item-number-retry", "工作项编号生成失败，请稍后重试");
        }
        return value;
    }
}
