package com.bluewhale.message.dispatch;

import com.bluewhale.message.enums.MessagePriority;

/**
 * 消息异步投递调度器。
 *
 * <p>在消息库里落库后把投递任务交给后台线程执行；实现需保证队列满时不丢消息，
 * 并按 {@link MessagePriority} 决定出队顺序。</p>
 *
 * @author BlueWhale
 */
public interface MessageDispatchExecutor {

    /**
     * 提交一条投递任务。
     *
     * @param priority 优先级；为 {@code null} 时按 {@link MessagePriority#NORMAL} 处理
     * @param task     投递任务
     */
    void submit(MessagePriority priority, Runnable task);
}
