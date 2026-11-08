package com.bluewhale.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务管理启动类
 *
 * <p>{@code @EnableScheduling} 不可省：调度中心的业务任务由自研调度器（{@code JobScheduler}）
 * 按 cron 自行触发，但「进程内工作泵」仍依赖 Spring 的 {@code @Scheduled}——
 * 例如 F-02 的多实例断连重对齐（{@code JobScheduleReconcileJob}）与注册表过期行清理。
 * 缺失该注解时这些任务会静默不执行。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableScheduling
public class WhaleJobApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleJobApplication.class, args);
    }
}
