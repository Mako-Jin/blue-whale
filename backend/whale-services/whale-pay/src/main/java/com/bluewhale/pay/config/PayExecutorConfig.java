package com.bluewhale.pay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 支付服务线程与调度配置。
 *
 * @author BlueWhale
 */
@Configuration
@EnableScheduling
public class PayExecutorConfig {

    /**
     * 通知投递线程池：回调 HTTP 慢操作与业务事务隔离。
     *
     * @return 线程池
     */
    @Bean("payNotifyExecutor")
    public ThreadPoolTaskExecutor payNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("pay-notify-");
        executor.initialize();
        return executor;
    }
}
