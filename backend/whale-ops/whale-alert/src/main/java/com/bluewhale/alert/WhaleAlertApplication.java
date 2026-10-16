package com.bluewhale.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 告警中心启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhaleAlertApplication.class, args);
    }
}
