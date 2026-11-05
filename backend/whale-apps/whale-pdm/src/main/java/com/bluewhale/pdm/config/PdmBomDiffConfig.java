package com.bluewhale.pdm.config;

import com.bluewhale.common.tenant.TenantContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * BOM 异步对比线程池配置
 *
 * <p>线程池挂载 {@link TenantContextTaskDecorator}，保证异步对比执行期间
 * 仍携带发起请求的租户上下文。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class PdmBomDiffConfig {

    /** 对比线程池 Bean 名称 */
    public static final String DIFF_EXECUTOR = "pdmDiffExecutor";

    /** 核心线程数 */
    private static final int CORE_POOL_SIZE = 2;

    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = 4;

    /** 队列容量 */
    private static final int QUEUE_CAPACITY = 100;

    /**
     * BOM 异步对比线程池。
     *
     * @return 带租户透传的线程池
     */
    @Bean(name = DIFF_EXECUTOR)
    public Executor pdmDiffExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("pdm-bom-diff-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }
}
