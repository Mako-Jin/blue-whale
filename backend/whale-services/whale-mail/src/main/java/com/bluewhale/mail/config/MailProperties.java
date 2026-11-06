package com.bluewhale.mail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮箱服务配置。
 *
 * <p>{@link #defaultImapHost} 等默认端点用于新建账号时兜底：多数部署只跑一套 Mailcow，
 * 让前端少填 6 个字段；接入外部企业邮箱时按账号覆盖即可。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.mail")
public class MailProperties {

    /** 默认邮箱域名，如 corp.example.com */
    private String defaultDomain;

    /** 默认 IMAP 主机 */
    private String defaultImapHost;

    /** 默认 IMAP 端口 */
    private int defaultImapPort = 993;

    /** 默认 IMAP 是否 SSL */
    private boolean defaultImapSsl = true;

    /** 默认 SMTP 主机 */
    private String defaultSmtpHost;

    /** 默认 SMTP 端口 */
    private int defaultSmtpPort = 587;

    /** 默认 SMTP 是否 SSL（465 为 true，587 STARTTLS 为 false） */
    private boolean defaultSmtpSsl = false;

    /** 是否开启定时增量同步；关闭时仅能手动触发 */
    private boolean syncEnabled = false;

    /** 单个账号单次同步上限（防止首同步拖垮服务） */
    private int maxMessagesPerSync = 200;

    /** 发信大小上限（字节，默认 25MB，与主流服务商一致） */
    private long maxSendSizeBytes = 25L * 1024 * 1024;

    /** 同步参数 */
    private Sync sync = new Sync();

    /** 文件服务集成参数 */
    private FileClient file = new FileClient();

    /** Mailcow 管理接口参数（平台代开邮箱用） */
    private Mailcow mailcow = new Mailcow();

    /**
     * 同步参数
     */
    @Data
    public static class Sync {

        /** 分页批量大小 */
        private int batchSize = 50;

        /** 定时同步间隔（毫秒） */
        private long fixedDelayMillis = 300_000L;

        /** 是否在同步阶段拉取正文（关闭时正文按需回源，推荐关闭） */
        private boolean fetchBody = false;
    }

    /**
     * 文件服务集成参数。
     *
     * <p>附件字节不落本库：收信时上传到 {@code whale-file} 只留 {@code fileId}，
     * 发信时按 {@code fileId} 取流组装 multipart。内网直连，不经网关。</p>
     */
    @Data
    public static class FileClient {

        /** 文件服务内网基址 */
        private String baseUrl = "http://whale-file:8085";

        /** 建连超时（毫秒） */
        private int connectTimeoutMillis = 3_000;

        /** 读取超时（毫秒）：附件可能是几十 MB，放宽到 60 秒 */
        private int readTimeoutMillis = 60_000;

        /**
         * 单个附件入库上限（字节）。
         *
         * <p>超过上限的附件不写入文件服务，但**仍登记元信息**并标注未缓存，
         * 避免用户「看不到这个附件」而误判邮件不完整。</p>
         */
        private long maxAttachmentBytes = 25L * 1024 * 1024;

        /** 单封邮件附件入库总上限（字节） */
        private long maxTotalAttachmentBytes = 50L * 1024 * 1024;
    }

    /**
     * Mailcow 管理接口参数。
     *
     * <p>只在「平台代开邮箱」时使用：{@code whale-auth} 建用户后回调本服务，
     * 由本服务调 Mailcow API 建真实邮箱并落账号索引。</p>
     *
     * <p>默认关闭。关闭时手工绑定流程完全不受影响——代开是增量能力，不是前置依赖。</p>
     */
    @Data
    public static class Mailcow {

        /** 是否启用平台代开 */
        private boolean enabled = false;

        /** Mailcow 基址，如 {@code https://mail.example.com}（必须带协议头） */
        private String baseUrl = "";

        /** 管理 API Key：全权限凭据，只能由环境变量注入，禁入配置中心明文与镜像 */
        private String apiKey = "";

        /** 新邮箱配额（MiB，0 表示不限） */
        private int quotaMb = 2048;

        /** 建连超时（毫秒） */
        private int connectTimeoutMillis = 3_000;

        /** 读取超时（毫秒） */
        private int readTimeoutMillis = 10_000;
    }
}
