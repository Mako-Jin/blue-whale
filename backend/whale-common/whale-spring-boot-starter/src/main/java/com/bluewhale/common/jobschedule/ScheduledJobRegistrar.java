package com.bluewhale.common.jobschedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * 受管定时任务注册器：扫描 {@link ScheduledJob} 并登记任务，必要时注册进程内 cron 调度。
 *
 * <p>扫描时机为单例初始化完成之后；为避免无谓实例化，先用 Bean 定义的类型判断是否含
 * {@link ScheduledJob}，命中才取 Bean 实例。通过 AOP 代理注册可调用方法，
 * 保证 {@code @Transactional} 等增强在任务执行时依然生效。</p>
 *
 * <p>远端模式（{@code whale.schedule.local-enabled=false}）下不创建调度线程池，
 * 只把方法登记进 {@link RemoteJobRegistry}，由调度中心触发。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class ScheduledJobRegistrar implements SmartInitializingSingleton, ApplicationContextAware,
        EnvironmentAware, DisposableBean {

    /** 调度线程名前缀 */
    private static final String THREAD_NAME_PREFIX = "whale-sched-";

    /** 进程内调度线程数：与原 {@code @Scheduled} 默认单线程相比适度放宽，避免任务互相阻塞 */
    private static final int POOL_SIZE = 4;

    /** 关闭时等待任务结束的秒数 */
    private static final int AWAIT_TERMINATION_SECONDS = 5;

    private final JobScheduleProperties properties;

    private final RemoteJobRegistry registry;

    private ApplicationContext applicationContext;

    private Environment environment;

    private ThreadPoolTaskScheduler scheduler;

    private final List<ScheduledFuture<?>> futures = new ArrayList<>();

    /**
     * 构造注册器。
     *
     * @param properties 配置项
     * @param registry   任务注册表
     */
    public ScheduledJobRegistrar(JobScheduleProperties properties, RemoteJobRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (applicationContext == null) {
            return;
        }
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = beanType(beanName);
            if (type == null || !hasScheduledJob(type)) {
                continue;
            }
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException e) {
                log.warn("受管任务 Bean 获取失败，已跳过: bean={}", beanName, e);
                continue;
            }
            scanBean(bean);
        }
        log.info("受管定时任务登记完成: localEnabled={}, jobCount={}, codes={}",
                properties.isLocalEnabled(), registry.codes().size(), registry.codes());
    }

    @Override
    public void destroy() {
        for (ScheduledFuture<?> future : futures) {
            future.cancel(false);
        }
        futures.clear();
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /**
     * 解析 Bean 的用户类型（剥离代理），非单例与容器内部 Bean 跳过。
     *
     * @param beanName Bean 名称
     * @return 用户类型；应跳过时返回 {@code null}
     */
    private Class<?> beanType(String beanName) {
        try {
            if (!applicationContext.isSingleton(beanName) || ScopedProxyUtils.isScopedTarget(beanName)) {
                return null;
            }
            Class<?> type = applicationContext.getType(beanName);
            if (type == null) {
                return null;
            }
            Class<?> userType = ClassUtils.getUserClass(type);
            return userType.getName().startsWith("org.springframework") ? null : userType;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 判断类型是否声明了受管任务方法。
     *
     * @param type 用户类型
     * @return 声明了至少一个 {@link ScheduledJob} 返回 {@code true}
     */
    private boolean hasScheduledJob(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (AnnotatedElementUtils.findMergedAnnotation(method, ScheduledJob.class) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描单个 Bean 上的受管任务方法并登记。
     *
     * @param bean Bean 实例（可能为 AOP 代理）
     */
    private void scanBean(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : targetClass.getDeclaredMethods()) {
            ScheduledJob annotation = AnnotatedElementUtils.findMergedAnnotation(method, ScheduledJob.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() > 0) {
                log.error("受管任务方法必须无参，已跳过: code={}, method={}",
                        annotation.code(), method.getName());
                continue;
            }
            Method invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
            registry.register(annotation.code(), bean, invocable);
            if (properties.isLocalEnabled()) {
                scheduleLocal(annotation);
            }
        }
    }

    /**
     * 注册进程内 cron 调度（行为与原 {@code @Scheduled} 一致）。
     *
     * @param annotation 任务注解
     */
    private void scheduleLocal(ScheduledJob annotation) {
        String cron = environment == null ? annotation.cron()
                : environment.resolvePlaceholders(annotation.cron());
        String code = annotation.code();
        try {
            ScheduledFuture<?> future = scheduler().schedule(() -> runQuietly(code), new CronTrigger(cron));
            futures.add(future);
            log.info("注册进程内定时任务: code={}, cron={}, timeoutSeconds={}",
                    code, cron, annotation.timeoutSeconds());
        } catch (RuntimeException e) {
            log.error("进程内定时任务注册失败（远程触发不受影响）: code={}, cron={}", code, cron, e);
        }
    }

    /**
     * 执行任务并吞掉异常：调度线程不能因单次失败而中断。
     *
     * @param code 任务编码
     */
    private void runQuietly(String code) {
        try {
            String result = registry.invoke(code);
            log.debug("进程内定时任务执行完成: code={}, result={}", code, result);
        } catch (RuntimeException e) {
            log.error("进程内定时任务执行失败: code={}", code, e);
        }
    }

    /**
     * 懒初始化调度线程池。
     *
     * @return 调度线程池
     */
    private synchronized ThreadPoolTaskScheduler scheduler() {
        if (scheduler == null) {
            ThreadPoolTaskScheduler created = new ThreadPoolTaskScheduler();
            created.setPoolSize(POOL_SIZE);
            created.setThreadNamePrefix(THREAD_NAME_PREFIX);
            created.setWaitForTasksToCompleteOnShutdown(true);
            created.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
            created.initialize();
            scheduler = created;
        }
        return scheduler;
    }
}
