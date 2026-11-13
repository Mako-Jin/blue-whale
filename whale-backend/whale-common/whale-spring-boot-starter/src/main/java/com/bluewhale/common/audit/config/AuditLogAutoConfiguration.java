package com.bluewhale.common.audit.config;

import com.bluewhale.common.audit.aop.AuditLogAspect;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.audit.properties.AuditLogForwardProperties;
import com.bluewhale.common.audit.support.AuditLogForwarder;
import com.bluewhale.common.audit.support.AuditLogMqPublisher;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.properties.SensitiveKeysProperties;
import com.bluewhale.common.mybatis.config.MybatisPlusConfig;
import com.bluewhale.common.tenant.TenantContextTaskDecorator;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 审计日志自动配置。
 * <p>生效条件：Servlet Web 应用 + 类路径存在 {@link AuditLogMapper}/{@code Aspect}/{@code ObjectMapper}
 * （即该服务具备数据源与 MyBatis 环境）。无数据源的服务（如 gateway、纯计算模块）不会装配审计切面。</p>
 *
 * <p><b>为什么不能用 {@code @ConditionalOnBean(AuditLogMapper.class)} 判 Mapper</b>：
 * {@code @ConditionalOnBean} 是<b>条件判定</b>，只在配置类解析期（{@code ConfigurationClassPostProcessor}）
 * 求值；而 {@link AuditLogMapper} 由 {@code @MapperScan} 的 {@code MapperScannerConfigurer}
 * （同为 BeanDefinitionRegistryPostProcessor，但优先级更低）在<b>其后</b>才注册 Bean 定义，
 * 故解析期条件<b>恒不成立</b>，切面永不装配（实证：真实服务 `audit_log` 落库 0 行）。
 * 正确做法：仅在 Bean 方法（<b>实例化期</b>，晚于全部 BeanFactoryPostProcessor，此时 Mapper 定义已就绪）
 * 通过 {@link ObjectProvider} 延迟解析 Mapper；静态可判定的条件（Web 类型 / Class 存在性）仍由
 * {@code @ConditionalOnWebApplication}/{@code @ConditionalOnClass} 承担。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration(after = MybatisPlusConfig.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({AuditLogMapper.class, Aspect.class, ObjectMapper.class})
@EnableConfigurationProperties(AuditLogForwardProperties.class)
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
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    /**
     * 敏感字段脱敏器：复用 Spring 管理的 Jackson 3 {@code ObjectMapper}，
     * 并入 {@code whale.audit.sensitive.keys} 附加敏感键名（AUTH-11 配置化）。
     *
     * @param objectMapper 全局 Jackson 序列化器
     * @param sensitiveKeysProvider 附加敏感键配置（未启用多通道日志的服务可能为空）
     * @return 敏感字段脱敏器
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMasker sensitiveDataMasker(ObjectMapper objectMapper,
                                                   ObjectProvider<SensitiveKeysProperties> sensitiveKeysProvider) {
        SensitiveKeysProperties sensitiveKeys = sensitiveKeysProvider.getIfAvailable();
        List<String> extraKeys = sensitiveKeys == null ? List.of() : sensitiveKeys.getKeys();
        return new SensitiveDataMasker(objectMapper, extraKeys);
    }

    /**
     * 审计日志转发器：仅在 {@code whale.audit.forward.enabled=true} 时装配。
     * <p>转发失败由 {@link AuditLogAspect} 回退本地落库；未开启时不装配，行为与现状一致。</p>
     *
     * @param properties 转发配置
     * @param objectMapper Spring 管理的 Jackson 序列化器
     * @return 审计日志转发器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "whale.audit.forward", name = "enabled", havingValue = "true")
    public AuditLogForwarder auditLogForwarder(AuditLogForwardProperties properties,
                                               ObjectMapper objectMapper) {
        return new AuditLogForwarder(properties, objectMapper);
    }

    /**
     * 审计切面。
     * <p>{@link AuditLogMapper} 在<b>本方法执行时</b>（Bean 实例化期）经 {@link ObjectProvider} 延迟解析，
     * 规避 {@code @ConditionalOnBean} 在配置类解析期早于 {@code @MapperScan} 的时序陷阱（详见类注释）；
     * 解析不到 Mapper（无数据源/未启用 MyBatis）时不装配切面。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(ObjectProvider<AuditLogMapper> auditLogMapperProvider,
                                         SensitiveDataMasker sensitiveDataMasker,
                                         @Qualifier("auditLogExecutor") Executor auditLogExecutor,
                                         Environment environment,
                                         ObjectProvider<AuditLogForwarder> auditLogForwarderProvider,
                                         ObjectProvider<AuditLogMqPublisher> auditLogMqPublisherProvider,
                                         AuditLogForwardProperties forwardProperties) {
        AuditLogMapper auditLogMapper = auditLogMapperProvider.getIfAvailable();
        if (auditLogMapper == null) {
            return null;
        }
        AuditLogForwarder auditLogForwarder = auditLogForwarderProvider.getIfAvailable();
        return new AuditLogAspect(auditLogMapper, sensitiveDataMasker, auditLogExecutor, environment,
                auditLogMqPublisherProvider.getIfAvailable(), auditLogForwarder,
                auditLogForwarder != null && forwardProperties.isLocalFallback());
    }
}
