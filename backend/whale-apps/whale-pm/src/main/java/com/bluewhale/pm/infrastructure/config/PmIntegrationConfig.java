package com.bluewhale.pm.infrastructure.config;

import com.bluewhale.common.tenant.TenantContextTaskDecorator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 集成能力配置：站内通知线程池与可选服务属性。
 *
 * <p>通知线程池挂载 {@link TenantContextTaskDecorator}，保证 {@code afterCommit} 异步投递时
 * 仍携带发起请求的租户上下文。</p>
 *
 * @author BlueWhale
 */
@Configuration
@EnableConfigurationProperties({PmNotifyProperties.class, PmApprovalProperties.class})
public class PmIntegrationConfig {

    /** 通知投递线程池 Bean 名称 */
    public static final String NOTIFY_EXECUTOR = "pmNotifyExecutor";

    /** 通知线程池核心线程数 */
    private static final int CORE_POOL_SIZE = 2;

    /** 通知线程池最大线程数 */
    private static final int MAX_POOL_SIZE = 4;

    /** 通知线程池队列容量 */
    private static final int QUEUE_CAPACITY = 200;

    /**
     * 站内通知投递线程池。
     *
     * @return 带租户透传的线程池
     */
    @Bean(name = NOTIFY_EXECUTOR)
    public Executor notifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("pm-notify-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }
}
