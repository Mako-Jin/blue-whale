package com.bluewhale.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 审计日志启动类
 *
 * <p>启用定时任务：日志保留期清理（{@code whale.audit.retention.*}，默认关闭）需要调度支撑。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class WhaleAuditApplication {

    /**
     * 启动审计日志应用
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleAuditApplication.class, args);
    }
}
