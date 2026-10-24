package com.bluewhale.archive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 档案服务启动类（Wave 5.53）。
 *
 * <p>服务端口 8093，对外一级路径前缀 {@code /archive}（经网关 {@code lb://whale-archive} 转发，不剥离前缀）。
 * 职责：档案门类、归档规则、档案条目、档号生成、不可变快照与检索；数据落 {@code bluewhale_archive}。
 * 文件实体仍由 {@code whale-file} 保管，本服务只持引用与锁定状态。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@ConfigurationPropertiesScan("com.bluewhale.archive")
public class WhaleArchiveApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleArchiveApplication.class, args);
    }
}
