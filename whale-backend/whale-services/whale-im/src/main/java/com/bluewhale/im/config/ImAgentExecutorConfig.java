package com.bluewhale.im.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 机器人流式调用线程池。
 *
 * <p>流式回答在独立线程执行，避免占用发送消息的 HTTP 请求线程；线程池由 Spring 管理，
 * 应用关闭时等待在途任务完成。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class ImAgentExecutorConfig {

    /**
     * 构造智能体调用线程池。
     *
     * @param poolSize 并发线程数
     * @return 线程池执行器
     */
    @Bean("imAgentExecutor")
    public ThreadPoolTaskExecutor imAgentExecutor(
            @Value("${whale.im.agent.pool-size:4}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("im-agent-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
