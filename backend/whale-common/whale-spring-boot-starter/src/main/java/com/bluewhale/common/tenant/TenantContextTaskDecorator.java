package com.bluewhale.common.tenant;

import org.springframework.core.task.TaskDecorator;

/**
 * 租户上下文线程池装饰器（Wave 5.54 Phase 2）。
 *
 * <p>提交任务时捕获当前线程租户，任务执行前绑定、执行后恢复原值，避免异步落库
 * （审计/日志）与索引任务丢失租户维度或串租户。</p>
 *
 * @author BlueWhale
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    /**
     * 包装任务：捕获提交线程的租户，执行线程绑定后运行，结束后恢复执行线程原租户
     *
     * @param runnable 原始任务
     * @return 携带租户上下文的包装任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Long captured = TenantContext.get();
        return () -> {
            Long previous = TenantContext.get();
            TenantContext.set(captured);
            try {
                runnable.run();
            } finally {
                TenantContext.set(previous);
            }
        };
    }
}
