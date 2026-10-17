package com.bluewhale.common.audit.config;

import com.bluewhale.common.audit.aop.AuditLogAspect;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.mybatis.config.MybatisPlusConfig;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 审计日志自动配置。
 * <p>生效条件：Servlet Web 应用 + 存在 {@link AuditLogMapper}（即该服务具备数据源与 MyBatis 环境）。
 * 无数据源的服务（如 gateway、纯计算模块）不会装配审计切面。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration(after = MybatisPlusConfig.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({AuditLogMapper.class, Aspect.class, ObjectMapper.class})
@ConditionalOnBean(AuditLogMapper.class)
public class AuditLogAutoConfiguration {

    /** 审计线程池核心线程数：审计为低频 IO，单线程足够 */
    private static final int CORE_POOL_SIZE = 1;
    /** 审计线程池最大线程数 */
    private static final int MAX_POOL_SIZE = 2;
    /** 审计线程池队列容量（有界，防止内存无限增长） */
    private static final int QUEUE_CAPACITY = 256;
    /** 关闭时等待未完成任务的最大秒数 */
    private static final int AWAIT_TERMINATION_SECONDS = 5;
    /** 审计线程名前缀，便于日志与线程栈排查 */
    private static final String THREAD_NAME_PREFIX = "audit-log-";

    /**
     * 审计落库线程池：有界队列 + {@code CallerRunsPolicy} 兜底（队列满时由调用线程执行，
     * 保证不丢记录且不新增拒绝路径）。审计不可成为业务可用性的单点。
     */
    @Bean(name = "auditLogExecutor")
    public ThreadPoolTaskExecutor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    /** 敏感字段脱敏器：复用 Spring 管理的 Jackson 3 {@code ObjectMapper} */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMasker sensitiveDataMasker(ObjectMapper objectMapper) {
        return new SensitiveDataMasker(objectMapper);
    }

    /** 审计切面 */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(AuditLogMapper auditLogMapper,
                                         SensitiveDataMasker sensitiveDataMasker,
                                         @Qualifier("auditLogExecutor") Executor auditLogExecutor,
                                         Environment environment) {
        return new AuditLogAspect(auditLogMapper, sensitiveDataMasker, auditLogExecutor, environment);
    }
}
