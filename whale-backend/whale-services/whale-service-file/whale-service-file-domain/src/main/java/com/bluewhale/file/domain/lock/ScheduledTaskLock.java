package com.bluewhale.file.domain.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * 定时任务分布式互斥端口：保证同一任务在同一时刻只被一个实例执行。
 *
 * <p>实现以租约（lease）语义持有锁：持有者需在租约到期前完成任务并释放；若进程异常退出，
 * 租约到期后其他实例可抢占。锁仅用于减少多实例重复扫描，不承载业务数据一致性职责。</p>
 *
 * @author BlueWhale
 */
public interface ScheduledTaskLock {

    /**
     * 尝试获取任务锁，不等待
     *
     * @param taskName 任务锁名称，需全局唯一且稳定
     * @param ttl 租约时长，必须为正
     * @return 获取成功时返回句柄（关闭即释放）；已被其他实例持有时返回空
     */
    Optional<Handle> tryAcquire(String taskName, Duration ttl);

    /**
     * 任务锁句柄：关闭即释放，重复关闭不报错
     */
    @FunctionalInterface
    interface Handle extends AutoCloseable {

        /**
         * 释放任务锁
         */
        @Override
        void close();
    }
}
