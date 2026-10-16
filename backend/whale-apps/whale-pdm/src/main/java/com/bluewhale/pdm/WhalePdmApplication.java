package com.bluewhale.pdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * PDM 产品数据管理启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class WhalePdmApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhalePdmApplication.class, args);
    }
}
