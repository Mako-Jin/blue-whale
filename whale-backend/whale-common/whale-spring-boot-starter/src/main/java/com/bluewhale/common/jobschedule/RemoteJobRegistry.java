package com.bluewhale.common.jobschedule;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 受管任务注册表：任务编码 → 执行方法。
 *
 * <p>与运行模式解耦——无论进程内调度是否开启，被 {@link ScheduledJob} 标注的方法都会登记到本表：
 * 进程内调度用它执行，调度中心则通过内网端点用它执行。两种模式共用同一份方法映射，
 * 保证「本地跑」与「远程跑」调用的是同一段逻辑。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class RemoteJobRegistry {

    /** 任务编码 → 执行器 */
    private final Map<String, Invoker> jobs = new ConcurrentHashMap<>();

    /** 本实例在途执行数：停机时等待其归零，避免任务随进程退出被半途杀掉 */
    private final AtomicInteger runningCount = new AtomicInteger();

    /** 等待在途执行结束的轮询间隔毫秒数 */
    private static final long IDLE_POLL_MILLIS = 50L;

    /**
     * 登记一个任务。
     *
     * @param code   任务编码
     * @param bean   目标 Bean（AOP 代理对象，保证事务等增强生效）
     * @param method 可调用方法（无参）
     */
    public void register(String code, Object bean, Method method) {
        if (method.getParameterCount() > 0) {
            log.error("受管任务方法必须无参，已跳过: code={}, method={}", code, method.getName());
            return;
        }
        Invoker previous = jobs.put(code, new Invoker(bean, method));
        if (previous != null) {
            log.warn("任务编码重复登记，后者覆盖前者: code={}, method={}", code, method.getName());
        }
        method.setAccessible(true);
    }

    /**
     * 执行指定任务。
     *
     * @param code 任务编码
     * @return 执行摘要（方法返回值字符串；无返回值返回 {@code OK}）
     */
    public String invoke(String code) {
        Invoker invoker = jobs.get(code);
        if (invoker == null) {
            throw new IllegalArgumentException("未注册的任务编码: " + code);
        }
        runningCount.incrementAndGet();
        try {
            Object result = invoker.method.invoke(invoker.bean);
            return result == null ? "OK" : String.valueOf(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("任务执行失败: " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("任务方法不可调用: " + code, e);
        } finally {
            runningCount.decrementAndGet();
        }
    }

    /**
     * 本实例当前在途执行数。
     *
     * @return 正在执行的任务数
     */
    public int runningCount() {
        return runningCount.get();
    }

    /**
     * 等待在途执行清零（有界等待）。
     *
     * @param timeoutMillis 最长等待毫秒数
     * @return 在时限内归零返回 {@code true}；超时仍有在途任务返回 {@code false}
     */
    public boolean awaitIdle(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            return runningCount.get() == 0;
        }
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (runningCount.get() > 0) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(IDLE_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return runningCount.get() == 0;
            }
        }
        return true;
    }

    /**
     * 是否已登记指定任务。
     *
     * @param code 任务编码
     * @return 已登记返回 {@code true}
     */
    public boolean contains(String code) {
        return jobs.containsKey(code);
    }

    /**
     * 已登记的任务编码集合（执行器注册时上报）。
     *
     * @return 不可变编码集合
     */
    public Set<String> codes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(jobs.keySet()));
    }

    /**
     * 任务执行器：持有目标 Bean 与可调用方法。
     *
     * @param bean   目标 Bean
     * @param method 可调用方法
     */
    private record Invoker(Object bean, Method method) {
    }
}
