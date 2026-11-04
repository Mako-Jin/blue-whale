package com.bluewhale.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户关系应用运行参数（配置前缀 {@code whale.crm}）。
 *
 * <p>{@link #demandGatePassed} 是业务功能总开关。Wave 5.83 已确认需求门禁通过
 * （REQ-FUNC-080..086 / REQ-ARCH-024 / REQ-SEC-034），默认放开 M0 与 M1 接口；
 * 生产环境仍可通过环境变量 {@code WHALE_CRM_DEMAND_GATE_PASSED=false} 一键回退到
 * 仅暴露 {@code /crm/meta} 的维护态。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.crm")
public class CrmProperties {

    /** 需求门禁是否通过；false 时仅暴露 /crm/meta */
    private boolean demandGatePassed = true;

    /** 客户编码前缀 */
    private String customerCodePrefix = "CUS";

    /** 跟进记录单页上限 */
    private int followUpPageLimit = 100;

    /** 是否允许合同相对方引用客户（合同上下文联调时开启） */
    private boolean contractReferenceEnabled = false;

    /** 客户默认等级 */
    private String defaultCustomerLevel = "C";

    /** 公海自动回收是否启用 */
    private boolean poolRecycleEnabled = false;

    /** 公海自动回收 Cron（默认每天 02:00） */
    private String poolRecycleCron = "0 0 2 * * ?";

    /** 单次公海回收最大条数 */
    private int poolRecycleBatchSize = 200;
}
