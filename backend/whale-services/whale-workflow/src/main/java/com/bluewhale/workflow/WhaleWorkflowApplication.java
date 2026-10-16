package com.bluewhale.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 工作流管理启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhaleWorkflowApplication.class, args);
    }
}
