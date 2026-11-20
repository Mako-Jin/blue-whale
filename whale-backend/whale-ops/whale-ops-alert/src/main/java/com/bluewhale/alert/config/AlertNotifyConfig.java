package com.bluewhale.alert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 告警通知线程池配置。
 *
 * <p>通知发送涉及邮件服务器、Webhook、短信网关等外部 IO，不能占用 Web 请求线程，
 * 也不应占用数据库事务。线程池有界，队列满时回退到调用线程执行
 * （{@link ThreadPoolExecutor.CallerRunsPolicy}），宁可让入站请求变慢也不丢通知。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class AlertNotifyConfig {

    /**
     * 通知发送专用线程池
     *
     * @return 有界线程池执行器
     */
    @Bean("alertNotifyExecutor")
    public Executor alertNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("alert-notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
