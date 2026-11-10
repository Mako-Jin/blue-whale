package com.bluewhale.oa.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * OA 通知线程池配置。
 *
 * <p>审批通知与审批主流程隔离：事务提交后异步投递，消息服务抖动不拉长审批接口耗时。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class OaNotificationExecutorConfig {

    /** 队列容量：通知为尽力而为，积压过多时丢弃，避免拖垮主流程 */
    private static final int QUEUE_CAPACITY = 200;

    /**
     * 审批通知线程池。
     *
     * @return 通知线程池
     */
    @Bean("oaNotificationExecutor")
    public ThreadPoolTaskExecutor oaNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("oa-notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
