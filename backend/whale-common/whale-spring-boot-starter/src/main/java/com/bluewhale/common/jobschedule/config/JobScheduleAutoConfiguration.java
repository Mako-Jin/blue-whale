package com.bluewhale.common.jobschedule.config;

import com.bluewhale.common.jobschedule.JobExecutorRegistrar;
import com.bluewhale.common.jobschedule.JobScheduleProperties;
import com.bluewhale.common.jobschedule.JobTriggerController;
import com.bluewhale.common.jobschedule.RemoteJobRegistry;
import com.bluewhale.common.jobschedule.ScheduledJobRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * 受管定时任务自动配置。
 *
 * <p>默认行为与引入前完全一致：{@link ScheduledJobRegistrar} 只在进程内注册 cron（等价 {@code @Scheduled}），
 * 内网触发端点与执行器注册**默认关闭**，避免影响既有的 20 多个服务。
 * 迁移中的服务按需打开：</p>
 *
 * <pre>
 * whale:
 *   schedule:
 *     local-enabled: false            # 交给调度中心触发
 *     trigger-enabled: true           # 暴露 /internal/job/{code}/run
 *     executor:
 *       register-enabled: true        # 向调度中心注册实例与任务编码
 *       server-url: http://bw-job:8082
 * </pre>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JobScheduleProperties.class)
public class JobScheduleAutoConfiguration {

    /**
     * 任务注册表：两种运行模式共用同一份方法映射。
     *
     * @return 任务注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public RemoteJobRegistry remoteJobRegistry() {
        return new RemoteJobRegistry();
    }

    /**
     * 任务注册器：扫描 {@code @ScheduledJob} 并按开关决定是否注册进程内调度。
     *
     * @param properties 配置项
     * @param registry   任务注册表
     * @return 注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public ScheduledJobRegistrar scheduledJobRegistrar(JobScheduleProperties properties,
                                                       RemoteJobRegistry registry) {
        return new ScheduledJobRegistrar(properties, registry);
    }

    /**
     * 内网触发端点：默认关闭，仅迁移中的服务显式开启。
     *
     * @param registry 任务注册表
     * @return 触发端点控制器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "whale.schedule", name = "trigger-enabled", havingValue = "true")
    public JobTriggerController jobTriggerController(RemoteJobRegistry registry) {
        return new JobTriggerController(registry);
    }

    /**
     * 执行器注册器：默认关闭，仅迁移中的服务显式开启。
     *
     * @param properties   配置项
     * @param registry     任务注册表
     * @param objectMapper JSON 解析器
     * @return 执行器注册器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "whale.schedule.executor", name = "register-enabled", havingValue = "true")
    public JobExecutorRegistrar jobExecutorRegistrar(JobScheduleProperties properties,
                                                     RemoteJobRegistry registry,
                                                     ObjectMapper objectMapper) {
        return new JobExecutorRegistrar(properties, registry, objectMapper);
    }
}
