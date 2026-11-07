package com.bluewhale.crm;

import com.bluewhale.crm.config.CrmArProperties;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import com.bluewhale.crm.config.CrmContractProperties;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.config.CrmInvoiceApprovalProperties;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmOrderApprovalProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmQuoteApprovalProperties;
import com.bluewhale.crm.config.CrmReturnApprovalProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.config.CrmDocsProperties;
import com.bluewhale.crm.config.CrmSurveyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 客户关系应用启动类（Wave 5.83，M0 底座 + M1 客户主数据）。
 *
 * <p>需求门禁已通过（REQ-FUNC-080..086 / REQ-ARCH-024 / REQ-SEC-034），
 * {@code whale.crm.demand-gate-passed=false} 可一键回退到仅 {@code /crm/meta} 的维护态。
 * 六上下文（customer / sales / commerce / service / marketing / analytics）与
 * integration / common 共用本模块化单体。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties({CrmProperties.class, CrmQuoteApprovalProperties.class, CrmContractProperties.class,
        CrmErpProperties.class, CrmOrderApprovalProperties.class, CrmInvoiceApprovalProperties.class,
        CrmArProperties.class, CrmServiceProperties.class, CrmDocsProperties.class, CrmSurveyProperties.class,
        CrmReturnApprovalProperties.class, CrmMarketingProperties.class, CrmAnalyticsProperties.class})
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
