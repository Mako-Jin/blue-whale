package com.bluewhale.file.infrastructure.lock;

import com.bluewhale.file.domain.lock.ScheduledTaskLock;
import com.bluewhale.file.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 {@code file_task_lock} 表的任务锁实现：租约 + 属主校验，不依赖 Redis。
 *
 * <p>抢锁为单条 {@code INSERT ... ON DUPLICATE KEY UPDATE}：仅当锁不存在或租约已过期时改写
 * 属主与到期时间；随后按「锁名 + 属主」回查确认归属，避免并发抢占时误判持有。释放按
 * 「锁名 + 属主」删除，非当前属主无法释放他人锁。每条语句独立提交，不需要长事务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MybatisScheduledTaskLock implements ScheduledTaskLock {

    /** 最小租约时长（秒）：配置异常时仍保留可抢占语义 */
    private static final int MIN_TTL_SECONDS = 1;

    private final FileInfoMapper fileInfoMapper;

    @Override
    public Optional<Handle> tryAcquire(String taskName, Duration ttl) {
        if (!StringUtils.hasText(taskName) || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Optional.empty();
        }
        String name = taskName.trim();
        String owner = UUID.randomUUID().toString();
        int ttlSeconds = (int) Math.min(Math.max(ttl.toSeconds(), MIN_TTL_SECONDS), Integer.MAX_VALUE);
        fileInfoMapper.tryAcquireTaskLock(name, owner, ttlSeconds);
        if (fileInfoMapper.countTaskLockOwner(name, owner) == 0) {
            return Optional.empty();
        }
        return Optional.of(() -> releaseQuietly(name, owner));
    }

    /**
     * 尽力释放任务锁：释放失败仅告警，租约到期后自动失效
     *
     * @param taskName 任务锁名称
     * @param owner 属主标识
     */
    private void releaseQuietly(String taskName, String owner) {
        try {
            fileInfoMapper.releaseTaskLock(taskName, owner);
        } catch (RuntimeException e) {
            log.warn("释放任务锁失败，等待租约到期: lock={}", taskName, e);
        }
    }
}
