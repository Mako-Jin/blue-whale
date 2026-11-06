package com.bluewhale.party;

import com.bluewhale.party.config.PartyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 党政工会应用启动类（Wave 5.88，M-P0 底座）。
 *
 * <p>需求门禁已通过（REQ-FUNC-094 / REQ-ARCH-026 / REQ-SEC-036），
 * {@code whale.party.demand-gate-passed=false} 可一键回退到仅 {@code /party/meta} 的维护态。</p>
 *
 * <p>六个业务上下文（branch / member / activity / fee / content / union）与 integration / common
 * 共用本模块化单体。服务边界：员工与组织主数据只读引用 ERP，附件字节归 {@code whale-file}，
 * 通知归 {@code whale-message}，慰问与福利审批归 {@code whale-workflow}；
 * <b>不读 {@code bluewhale_oa} 的任何表</b>。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(PartyProperties.class)
public class WhalePartyApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhalePartyApplication.class, args);
    }
}
