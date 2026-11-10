package com.bluewhale.lms;

import com.bluewhale.lms.config.LmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 学习培训考试应用启动类（Wave 5.87，M0 底座 + M1 课程与学习）。
 *
 * <p>需求门禁已通过（REQ-FUNC-087..090 / REQ-ARCH-025 / REQ-SEC-035），
 * {@code whale.lms.demand-gate-passed=false} 可一键回退到仅 {@code /lms/meta} 的维护态。
 * 五上下文（course / training / exam / cert / analytics）与 integration / common
 * 共用本模块化单体；党政工会归 whale-oa，不在本服务承载。</p>
 *
 * <p>{@code @EnableScheduling} 供 M5 的每日催办任务（{@code LmsUrgeJob}）使用。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(LmsProperties.class)
public class WhaleLmsApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleLmsApplication.class, args);
    }
}
