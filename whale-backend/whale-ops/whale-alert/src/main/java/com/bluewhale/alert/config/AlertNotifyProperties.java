package com.bluewhale.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警通知配置，前缀 {@code whale.alert.notify}。
 *
 * <p>通道由告警规则的 {@code channel} 字段选择；具体收件人 / Webhook 地址为全局配置，
 * 避免为每条规则单独维护目标。邮件通道依赖 {@code spring.mail.host}，未配置时
 * {@link org.springframework.mail.javamail.JavaMailSender} 不会创建，发送自动跳过。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.alert.notify")
public class AlertNotifyProperties {

    /** 邮件通道配置 */
    private Email email = new Email();

    /** Webhook 通道配置 */
    private Webhook webhook = new Webhook();

    /** 短信通道配置（F-03b：经统一 HTTP 短信网关发送） */
    private Sms sms = new Sms();

    /** Prometheus Alertmanager 入站校验令牌（为空时拒绝所有入站告警） */
    private String webhookToken;

    /** 入站告警去重配置（F-03） */
    private Dedup dedup = new Dedup();

    /**
     * 入站告警去重配置：同一 fingerprint 在窗口内重复触发时只落库/通知一次。
     *
     * @author BlueWhale
     */
    @Data
    public static class Dedup {

        /** 是否启用去重 */
        private boolean enabled = true;

        /** 去重窗口（秒），窗口内同 fingerprint 的 firing 告警不再重复落库与通知 */
        private int windowSeconds = 300;
    }

    /**
     * 邮件通道配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Email {

        /** 是否启用邮件通道 */
        private boolean enabled;

        /** 发件人地址 */
        private String from;

        /** 收件人列表（支持逗号分隔配置） */
        private List<String> to = new ArrayList<>();
    }

    /**
     * Webhook 通道配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Webhook {

        /** 是否启用 Webhook 通道 */
        private boolean enabled;

        /** 目标地址（HTTP/HTTPS） */
        private String url;

        /** 连接与请求超时（毫秒） */
        private int timeoutMillis = 3000;
    }

    /**
     * 短信通道配置。
     *
     * <p>不绑定具体供应商：由部署侧提供统一 HTTP 网关，平台按固定 JSON 协议提交手机号、
     * 模板编码、签名与告警内容。供应商专有签名算法应在网关内完成，平台不保存短信供应商密钥
     * 之外的协议细节。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Sms {

        /** 是否启用短信通道 */
        private boolean enabled;

        /** 短信网关地址（HTTP/HTTPS） */
        private String url;

        /** 网关 Bearer Token；生产环境必须配置，为空时仅允许本地联调 */
        private String token;

        /** 接收手机号列表（支持逗号分隔配置） */
        private List<String> to = new ArrayList<>();

        /** 供应商模板编码 */
        private String templateCode = "BLUEWHALE_ALERT";

        /** 供应商短信签名 */
        private String signName;

        /** 连接与请求超时（毫秒） */
        private int timeoutMillis = 3000;
    }
}
