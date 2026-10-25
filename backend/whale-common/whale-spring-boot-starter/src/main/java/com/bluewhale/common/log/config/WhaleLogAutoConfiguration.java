package com.bluewhale.common.log.config;

import com.bluewhale.common.audit.config.AuditLogAutoConfiguration;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.filter.ApiLogFilter;
import com.bluewhale.common.log.interceptor.OutboundLogInterceptor;
import com.bluewhale.common.log.interceptor.OutboundLogRestTemplateCustomizer;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import com.bluewhale.common.log.properties.ApiLogProperties;
import com.bluewhale.common.log.properties.LogSinkProperties;
import com.bluewhale.common.log.properties.SensitiveKeysProperties;
import com.bluewhale.common.log.sink.LocalLogSink;
import com.bluewhale.common.log.sink.LogPersistSupport;
import com.bluewhale.common.log.sink.LogSink;
import com.bluewhale.common.mybatis.config.MybatisPlusConfig;
import com.bluewhale.common.tenant.TenantContextTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 审计日志多通道自动配置（REQ-SEC-006 A7）。
 *
 * <p>生效条件：Servlet Web 应用 + 类路径存在 {@link ApiLogMapper}/{@code ObjectMapper}
 * （即该服务具备数据源与 MyBatis/Jackson 环境）。注册独立线程池 {@code whaleLogExecutor}、
 * {@link LogSink}/{@link LogPersistSupport}、接口日志过滤器与出站日志拦截器。</p>
 *
 * <p><b>为什么不能用 {@code @ConditionalOnBean(ApiLogMapper.class)}</b>：{@code @ConditionalOnBean}
 * 在配置类解析期求值，早于 {@code @MapperScan} 注册 Mapper Bean 定义，条件恒不成立致组件永不装配；
 * 正确做法是在 Bean 方法（实例化期）内用 {@link ObjectProvider} 延迟解析 Mapper，解析不到返回 {@code null}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@AutoConfiguration(after = {MybatisPlusConfig.class, AuditLogAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ApiLogMapper.class, ObjectMapper.class})
@EnableConfigurationProperties({ApiLogProperties.class, LogSinkProperties.class, SensitiveKeysProperties.class})
public class WhaleLogAutoConfiguration {

    /** 日志线程池核心线程数 */
    private static final int CORE_POOL_SIZE = 2;
    /** 日志线程池最大线程数 */
    private static final int MAX_POOL_SIZE = 4;
    /** 日志线程池队列容量（有界，防止内存无限增长） */
    private static final int QUEUE_CAPACITY = 2048;
    /** 关闭时等待未完成任务的最大秒数 */
    private static final int AWAIT_TERMINATION_SECONDS = 5;
    /** 日志线程名前缀，便于日志与线程栈排查 */
    private static final String THREAD_NAME_PREFIX = "whale-log-";

    /**
     * 日志落库线程池：独立于 {@code auditLogExecutor}（避免高频接口日志打满并阻塞审计），
     * 有界队列 + {@code CallerRunsPolicy} 兜底（队列满时由调用线程执行，保证不丢记录）。
     *
     * @return 日志落库线程池
     */
    @Bean(name = "whaleLogExecutor")
    public ThreadPoolTaskExecutor whaleLogExecutor() {
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
     * 日志落库 sink：{@code whale.audit.sink=mq} 未实现时 fail-fast 提示并回落 local（仅告警，不抛）
     *
     * @param whaleLogExecutor 日志落库线程池
     * @param properties 日志 sink 配置
     * @return 本地异步直写 sink
     */
    @Bean
    @ConditionalOnMissingBean
    public LogSink logSink(@Qualifier("whaleLogExecutor") Executor whaleLogExecutor, LogSinkProperties properties) {
        if (LogSinkProperties.SINK_MQ.equals(properties.getSink())) {
            log.warn("whale.audit.sink=mq 尚未实现，已回落 local（fail-fast 提示，不阻断启动）");
        }
        return new LocalLogSink(whaleLogExecutor);
    }

    /**
     * 日志落库支撑：统一「事务边界 + 异步投递 + 异常仅告警」策略
     *
     * @param logSink 日志 sink
     * @return 日志落库支撑
     */
    @Bean
    @ConditionalOnMissingBean
    public LogPersistSupport logPersistSupport(LogSink logSink) {
        return new LogPersistSupport(logSink);
    }

    /**
     * 后端接口日志过滤器。
     * <p>{@link ApiLogMapper} 在本方法执行时（Bean 实例化期）经 {@link ObjectProvider} 延迟解析，
     * 规避 {@code @ConditionalOnBean} 在配置类解析期早于 {@code @MapperScan} 的时序陷阱；
     * 解析不到 Mapper（无数据源/未启用 MyBatis）时不装配过滤器。</p>
     *
     * @param apiLogProperties 接口日志配置
     * @param logPersistSupport 落库支撑
     * @param apiLogMapperProvider 接口日志 Mapper 延迟提供者
     * @param sensitiveDataMasker 敏感数据脱敏器
     * @return 接口日志过滤器；无 Mapper 时返回 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiLogFilter apiLogFilter(ApiLogProperties apiLogProperties,
                                     LogPersistSupport logPersistSupport,
                                     ObjectProvider<ApiLogMapper> apiLogMapperProvider,
                                     SensitiveDataMasker sensitiveDataMasker) {
        ApiLogMapper apiLogMapper = apiLogMapperProvider.getIfAvailable();
        if (apiLogMapper == null) {
            return null;
        }
        return new ApiLogFilter(apiLogProperties, logPersistSupport, apiLogMapper, sensitiveDataMasker);
    }

    /**
     * 出站三方调用日志拦截器。
     * <p>{@link OutboundLogMapper} 同样经 {@link ObjectProvider} 实例化期延迟解析，解析不到返回 {@code null}。</p>
     *
     * @param outboundLogMapperProvider 出站日志 Mapper 延迟提供者
     * @param sensitiveDataMasker 敏感数据脱敏器
     * @param logPersistSupport 落库支撑
     * @return 出站日志拦截器；无 Mapper 时返回 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboundLogInterceptor outboundLogInterceptor(ObjectProvider<OutboundLogMapper> outboundLogMapperProvider,
                                                         SensitiveDataMasker sensitiveDataMasker,
                                                         LogPersistSupport logPersistSupport) {
        OutboundLogMapper outboundLogMapper = outboundLogMapperProvider.getIfAvailable();
        if (outboundLogMapper == null) {
            return null;
        }
        return new OutboundLogInterceptor(outboundLogMapper, sensitiveDataMasker, logPersistSupport);
    }

    /**
     * 出站日志 RestTemplate 定制器：经 {@code RestTemplateBuilder} 自动挂载拦截器（业务零改动）。
     *
     * @param interceptorProvider 出站日志拦截器延迟提供者（无 Mapper 时为空）
     * @return 出站日志 RestTemplate 定制器；无拦截器时返回 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboundLogRestTemplateCustomizer outboundLogRestTemplateCustomizer(
            ObjectProvider<OutboundLogInterceptor> interceptorProvider) {
        OutboundLogInterceptor interceptor = interceptorProvider.getIfAvailable();
        if (interceptor == null) {
            return null;
        }
        return new OutboundLogRestTemplateCustomizer(interceptor);
    }
}
