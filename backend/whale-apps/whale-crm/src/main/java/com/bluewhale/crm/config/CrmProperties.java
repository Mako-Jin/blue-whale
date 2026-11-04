package com.bluewhale.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户关系应用运行参数（配置前缀 {@code whale.crm}）。
 *
 * <p>{@link #demandGatePassed} 是唯一的功能开关：默认 {@code false}，业务接口全部不启用；
 * 只有需求门禁评审通过、ADR-003 定稿后才允许改为 {@code true} 并进入 M4 编码。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.crm")
public class CrmProperties {

    /** 需求门禁是否通过（D2）；false 时仅暴露 /crm/meta */
    private boolean demandGatePassed = false;

    /** 客户编码前缀（M4 使用，门禁通过后生效） */
    private String customerCodePrefix = "CUS";

    /** 跟进记录单页上限（M5 使用，门禁通过后生效） */
    private int followUpPageLimit = 100;

    /** 是否允许合同相对方引用客户（M5 使用，门禁通过后生效） */
    private boolean contractReferenceEnabled = false;
}
