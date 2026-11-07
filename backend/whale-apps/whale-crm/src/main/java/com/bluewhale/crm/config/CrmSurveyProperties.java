package com.bluewhale.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 满意度问卷配置（配置前缀 {@code whale.crm.survey}）。
 *
 * <p>默认关闭避免误发；令牌只存 HMAC-SHA256 哈希，密钥留空时进程内随机生成
 * （重启后历史令牌失效），生产必须显式配置 {@code whale.crm.survey.secret}。</p>
 *
 * @author BlueWhale
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "whale.crm.survey")
public class CrmSurveyProperties {

    /** 是否启用满意度问卷 */
    private boolean enabled = false;

    /** 令牌有效期（天） */
    private int expireDays = 7;

    /** 令牌 HMAC 密钥（生产必须配置） */
    private String secret;

    /** 同 IP + 工单限流窗口（分钟） */
    private int rateLimitMinutes = 10;

    /** 限流窗口内最大提交次数 */
    private int rateLimitCount = 5;
}
