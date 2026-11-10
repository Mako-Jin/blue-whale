package com.bluewhale.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 营销管理（M5 活动 / 人群 / 培育 / 触达 / 渠道）配置。
 *
 * <p>D9 口径：邮件 / 短信 / 站内信三渠道走 {@code whale-message} 投递；
 * 同一活动同一联系人在同一渠道只触达一次；ROI 默认「活动归因赢单金额 ÷ 实际费用」。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.crm.marketing")
public class CrmMarketingProperties {

    /** 营销模块开关 */
    private boolean enabled = true;

    /** 单批次触达收件人上限 */
    private int sendBatchSize = 200;

    /** 单活动同一联系人每日触达次数上限 */
    private int dailyFrequencyLimit = 1;

    /** 默认触达渠道（EMAIL / SMS / INBOX） */
    private String defaultChannel = "EMAIL";

    /** 消息服务内网地址 */
    private String messageUrl = "http://127.0.0.1:8094";

    /** 送达回执回调地址（消息服务回传渠道回执的内网端点；为空则不再回传） */
    private String callbackUrl = "http://127.0.0.1:9007/crm/internal/marketing/delivery-callback";

    /** 消息服务连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 消息服务读取超时（毫秒） */
    private int readTimeoutMillis = 3000;

    /** 渠道报单自动通过开关（关闭时一律人工裁决） */
    private boolean autoApproveReport = false;
}
