package com.bluewhale.message.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步消息投递线程池配置。
 *
 * <p>队列满时使用 {@link ThreadPoolExecutor.CallerRunsPolicy} 回压到提交线程，
 * 宁可让调用方承担投递耗时，也不丢弃消息。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class MessageDispatchConfig {

    /** 异步投递线程池 Bean 名称 */
    public static final String DISPATCH_EXECUTOR = "messageDispatchExecutor";

    /**
     * 注册异步投递线程池。
     *
     * @param properties 投递配置
     * @return 线程池
     */
    @Bean(name = DISPATCH_EXECUTOR)
    public ThreadPoolTaskExecutor messageDispatchExecutor(MessageSendProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getPoolSize());
        executor.setMaxPoolSize(properties.getPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("message-dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
