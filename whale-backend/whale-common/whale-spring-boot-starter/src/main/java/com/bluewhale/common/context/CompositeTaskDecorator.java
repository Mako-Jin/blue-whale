package com.bluewhale.common.context;

import org.springframework.core.task.TaskDecorator;

/**
 * 任务装饰器组合：按声明顺序依次包装任务，用于同时传递租户、语言等多个上下文。
 *
 * <p>例如 {@code new CompositeTaskDecorator(new TenantContextTaskDecorator(),
 * new LocaleContextTaskDecorator())} 会在同一线程池任务中同时恢复租户与语言。</p>
 *
 * @author BlueWhale
 */
public class CompositeTaskDecorator implements TaskDecorator {

    /**
     * 被组合的装饰器
     */
    private final TaskDecorator[] delegates;

    /**
     * 构造组合装饰器
     *
     * @param delegates 被组合的装饰器，按顺序应用
     */
    public CompositeTaskDecorator(TaskDecorator... delegates) {
        this.delegates = delegates == null ? new TaskDecorator[0] : delegates.clone();
    }

    /**
     * 依次应用所有装饰器。
     *
     * @param runnable 原始任务
     * @return 组合包装后的任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Runnable decorated = runnable;
        for (TaskDecorator delegate : delegates) {
            decorated = delegate.decorate(decorated);
        }
        return decorated;
    }
}
