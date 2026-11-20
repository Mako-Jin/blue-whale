package com.bluewhale.common.i18n;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskDecorator;

import java.util.Locale;

/**
 * 语言上下文线程池装饰器。
 *
 * <p>提交任务时捕获当前线程 Locale，执行前绑定、执行后恢复，避免异步落库或异步通知
 * 丢失语言维度（线程池复用会把上一个请求的语言带到下一个任务）。</p>
 *
 * @author BlueWhale
 */
public class LocaleContextTaskDecorator implements TaskDecorator {

    /**
     * 包装任务：捕获提交线程的语言，执行线程绑定后运行，结束后恢复执行线程原值。
     *
     * @param runnable 原始任务
     * @return 携带语言上下文的包装任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Locale captured = LocaleContextHolder.getLocale();
        return () -> {
            Locale previous = LocaleContextHolder.getLocale();
            LocaleContextHolder.setLocale(captured);
            try {
                runnable.run();
            } finally {
                LocaleContextHolder.setLocale(previous);
            }
        };
    }
}
