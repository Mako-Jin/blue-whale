package com.bluewhale.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息渠道配置。
 *
 * <p>{@link #mock} 是全局开关：开启时 EMAIL/SMS/IM 均不发起真实网络请求，只记录
 * {@code MOCKED}。无真实供应商阶段默认开启，生产接入时必须显式关闭并配置对应渠道。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.message")
public class MessageChannelProperties {

    /** 全局 mock 开关：true 时外部通道不发起真实请求 */
    private boolean mock = true;

    /** 邮件渠道 */
    private Email email = new Email();

    /** 短信渠道 */
    private Sms sms = new Sms();

    /** IM 渠道 */
    private Im im = new Im();

    /**
     * 邮件渠道配置
     *
     * <p>回执协议：发送时为每封邮件写入 RFC 5322 {@code Message-ID} 头并作为
     * {@code providerMessageId} 落库；退信/已读回执（DSN/MDN）原样携带该头，
     * 上报回执时填入 {@code providerMessageId} 即可精确匹配。</p>
     */
    @Data
    public static class Email {

        /** 是否启用 */
        private boolean enabled;

        /** 发件人地址 */
        private String from;

        /** Message-ID 域名部分（生成 {@code <msg.id.uuid@domain>}） */
        private String messageIdDomain = "bluewhale.local";

        /** 限流与熔断 */
        private Guard guard = new Guard();
    }

    /**
     * 短信渠道配置（部署侧统一 HTTP 网关）
     *
     * <p>回执协议：网关同步响应返回消息标识，默认取 JSON {@code data.messageId}；
     * 网关异步回执以同一标识回传，上报时填入 {@code providerMessageId}。
     * 响应为 JSON 且存在业务码时按 {@code successCodeField}/{@code successCode} 判定成败。</p>
     */
    @Data
    public static class Sms {

        /** 是否启用 */
        private boolean enabled;

        /** 网关地址 */
        private String url;

        /** 网关 Bearer Token（可选） */
        private String token;

        /** 供应商模板编码 */
        private String templateCode = "BLUEWHALE_MESSAGE";

        /** 供应商短信签名 */
        private String signName = "蓝鲸";

        /** 业务码字段名（点号路径，空表示不校验） */
        private String successCodeField = "code";

        /** 业务成功码 */
        private int successCode = 0;

        /** 供应商消息标识字段（点号路径，空表示不解析） */
        private String providerIdField = "data.messageId";

        /** 超时（毫秒） */
        private int timeoutMillis = 3000;

        /** 限流与熔断 */
        private Guard guard = new Guard();
    }

    /**
     * IM 渠道配置（内网调用 whale-im）
     *
     * <p>回执协议：消息服务以 {@code msg-<消息ID>-<用户ID>} 作为 {@code clientMsgId} 投递，
     * whale-im 的送达/已读回执按该字段回传，上报时填入 {@code providerMessageId}。</p>
     */
    @Data
    public static class Im {

        /** 是否启用 */
        private boolean enabled;

        /** whale-im 基础地址，如 http://127.0.0.1:8091 */
        private String baseUrl;

        /** 超时（毫秒） */
        private int timeoutMillis = 3000;

        /** 限流与熔断 */
        private Guard guard = new Guard();
    }

    /**
     * 渠道级限流与熔断配置
     *
     * <p>默认不开启限流（{@code rateLimitPerSecond=0}）；熔断默认连续 10 次失败后打开 30 秒。
     * 置 {@code failureThreshold=0} 可关闭熔断。</p>
     */
    @Data
    public static class Guard {

        /** 每秒允许发送条数，非正数表示不限流 */
        private int rateLimitPerSecond;

        /** 连续失败多少次后熔断，非正数表示不熔断 */
        private int failureThreshold = 10;

        /** 熔断保持时间（毫秒） */
        private long openMillis = 30000L;
    }
}
