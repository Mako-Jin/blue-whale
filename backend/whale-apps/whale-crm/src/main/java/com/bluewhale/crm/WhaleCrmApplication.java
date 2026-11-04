package com.bluewhale.crm;

import com.bluewhale.crm.config.CrmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 客户关系应用启动类（Wave 5.77，规划态模块骨架）。
 *
 * <p><b>需求门禁（ADR-003）未通过前，本应用只提供 {@code /crm/meta} 门禁状态查询，
 * 不开放任何客户 / 联系人 / 商机 / 跟进业务接口。</b>数据库表结构与权限点先登记，
 * 便于门禁通过后直接进入 M4，避免二次评审与返工。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(CrmProperties.class)
public class WhaleCrmApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleCrmApplication.class, args);
    }
}
