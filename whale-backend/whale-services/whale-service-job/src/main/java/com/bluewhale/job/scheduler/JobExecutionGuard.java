package com.bluewhale.job.scheduler;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行守卫：提供实例内互斥与「待补跑一次」标记的并发安全存储。
 *
 * <p>用于保证同一任务在单实例内不会被并发执行（cron 触发与手动触发共享同一互斥），
 * 并在发生触发重叠（上次执行未结束）时按 misfire 策略登记一次性补跑。
 * 与 {@link JobScheduler} 中的分布式锁配合，覆盖单实例与多实例两种重复执行场景。</p>
 *
 * @author BlueWhale
 */
@Component
public class JobExecutionGuard {

    /** 当前实例正在执行的任务ID集合 */
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    /** 待补跑一次的任务ID集合 */
    private final Set<Long> pendingOnce = ConcurrentHashMap.newKeySet();

    /**
     * 尝试进入执行区（实例内互斥）。
     *
     * @param jobId 任务ID
     * @return 成功抢占返回 {@code true}；同一任务已在执行返回 {@code false}
     */
    public boolean tryEnter(Long jobId) {
        return running.add(jobId);
    }

    /**
     * 退出执行区，释放实例内互斥。
     *
     * @param jobId 任务ID
     */
    public void exit(Long jobId) {
        running.remove(jobId);
    }

    /**
     * 登记该任务待补跑一次。
     *
     * @param jobId 任务ID
     */
    public void markPendingOnce(Long jobId) {
        pendingOnce.add(jobId);
    }

    /**
     * 消费待补跑标记。
     *
     * @param jobId 任务ID
     * @return 命中并移除返回 {@code true}；无待补跑标记返回 {@code false}
     */
    public boolean consumePendingOnce(Long jobId) {
        return pendingOnce.remove(jobId);
    }

    /**
     * 判断任务是否正在本实例执行。
     *
     * @param jobId 任务ID
     * @return 执行中返回 {@code true}
     */
    public boolean isRunning(Long jobId) {
        return running.contains(jobId);
    }
}
