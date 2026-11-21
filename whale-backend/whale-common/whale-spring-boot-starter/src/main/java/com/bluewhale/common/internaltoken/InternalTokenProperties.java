package com.bluewhale.common.internaltoken;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务侧内网接口令牌校验配置（V-02 纵深防御）。
 *
 * <p>网关侧 {@code internal-deny} 只能阻止「经网关」的外部访问；直连业务端口的
 * {@code /internal/**} 请求由本组件在服务侧校验共享令牌（请求头 {@code X-Internal-Token}）。</p>
 *
 * <p><b>两种模式：</b></p>
 * <ol>
 *   <li><b>共享令牌（默认，兼容存量）</b>：所有服务共用一个令牌，令牌解析顺序为显式配置
 *       {@code bluewhale.internal-token.token} 优先，为空时回退环境变量
 *       {@code WHALE_INTERNAL_TOKEN}；两者均为空时 dev 仅告警放行、非 dev 由
 *       {@link InternalTokenGuard} 拒绝启动。</li>
 *   <li><b>按服务签发（推荐，P0-1）</b>：配置 {@link #issuers} 后，请求头值改为
 *       {@code <服务ID>.<密钥>}，服务端按服务 ID 查表校验密钥，并可按
 *       {@link Issuer#getTenants()} 限制该服务可访问的租户；{@link Issuer#isPlatform()}
 *       为 {@code true} 时允许跨租户（平台级调用）。此时共享令牌不再被接受。</li>
 * </ol>
 *
 * <p>出站侧配置 {@link #issuerId}/{@link #issuerSecret} 声明本服务身份，
 * {@code InternalTokenSupport} 自动以 {@code <服务ID>.<密钥>} 形式发送。</p>
 *
 * @author BlueWhale
 */
@ConfigurationProperties(prefix = "bluewhale.internal-token")
public class InternalTokenProperties {

    /** 默认令牌请求头名称 */
    private static final String DEFAULT_HEADER = "X-Internal-Token";

    /** 默认租户请求头名称（与 {@code GatewayContract.Headers.TENANT_ID} 一致） */
    private static final String DEFAULT_TENANT_HEADER = "X-Tenant-Id";

    /** 令牌环境变量名 */
    private static final String TOKEN_ENV = "WHALE_INTERNAL_TOKEN";

    /** 是否启用服务侧内网接口令牌校验 */
    private boolean enabled = true;

    /** 令牌请求头名称 */
    private String header = DEFAULT_HEADER;

    /** 租户请求头名称：按服务令牌校验租户归属时读取 */
    private String tenantHeader = DEFAULT_TENANT_HEADER;

    /** 共享内网令牌；为空时回退读取环境变量 {@code WHALE_INTERNAL_TOKEN} */
    private String token = "";

    /** 本服务出站调用身份 ID（按服务签发模式下必填，与服务端 issuers 的 key 对应） */
    private String issuerId = "";

    /** 本服务出站调用密钥（与 {@link #issuerId} 成对配置） */
    private String issuerSecret = "";

    /** 入站校验的服务令牌清单：key 为调用方服务 ID，value 为其密钥与可访问租户 */
    private Map<String, Issuer> issuers = new LinkedHashMap<>();

    /** 受保护路径模式（Ant 通配），默认覆盖所有含 internal 段的路径与审计摄入端点 */
    private List<String> pathPatterns = new ArrayList<>(List.of("/**/internal/**", "/audit/v1/log/ingest/**"));

    /** 豁免路径模式：命中即跳过令牌校验（如 Alertmanager 入站 webhook，自身已带告警令牌） */
    private List<String> excludePatterns = new ArrayList<>();

    /**
     * 解析生效令牌：显式配置优先，其次环境变量
     *
     * @return 生效令牌；可能为空串（未配置）
     */
    public String resolveToken() {
        if (token != null && !token.isBlank()) {
            return token;
        }
        String fromEnv = System.getenv(TOKEN_ENV);
        return fromEnv == null ? "" : fromEnv.trim();
    }

    /**
     * 是否启用「按服务签发」模式（配置了任一入站服务令牌即为该模式）
     *
     * @return 启用返回 {@code true}
     */
    public boolean hasIssuers() {
        return issuers != null && !issuers.isEmpty();
    }

    /**
     * 解析出站请求应携带的令牌：本服务身份成对配置时用 {@code <服务ID>.<密钥>}，否则回退共享令牌
     *
     * @return 出站令牌；可能为空串（未配置）
     */
    public String resolveOutboundToken() {
        if (StringUtils.hasText(issuerId) && StringUtils.hasText(issuerSecret)) {
            return issuerId.trim() + "." + issuerSecret.trim();
        }
        return resolveToken();
    }

    /**
     * 是否启用服务侧内网令牌校验
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用服务侧内网令牌校验
     *
     * @param enabled 启用开关
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 令牌请求头名称
     *
     * @return 头名称
     */
    public String getHeader() {
        return header;
    }

    /**
     * 设置令牌请求头名称
     *
     * @param header 头名称
     */
    public void setHeader(String header) {
        this.header = header;
    }

    /**
     * 显式配置的共享内网令牌
     *
     * @return 令牌（可能为空串）
     */
    public String getToken() {
        return token;
    }

    /**
     * 设置显式共享内网令牌
     *
     * @param token 令牌
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 受保护路径模式列表
     *
     * @return Ant 模式列表
     */
    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    /**
     * 设置受保护路径模式列表
     *
     * @param pathPatterns Ant 模式列表
     */
    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns;
    }

    /**
     * 租户请求头名称
     *
     * @return 头名称
     */
    public String getTenantHeader() {
        return tenantHeader;
    }

    /**
     * 设置租户请求头名称
     *
     * @param tenantHeader 头名称
     */
    public void setTenantHeader(String tenantHeader) {
        this.tenantHeader = tenantHeader;
    }

    /**
     * 本服务出站调用身份 ID
     *
     * @return 服务 ID（可能为空串）
     */
    public String getIssuerId() {
        return issuerId;
    }

    /**
     * 设置本服务出站调用身份 ID
     *
     * @param issuerId 服务 ID
     */
    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    /**
     * 本服务出站调用密钥
     *
     * @return 密钥（可能为空串）
     */
    public String getIssuerSecret() {
        return issuerSecret;
    }

    /**
     * 设置本服务出站调用密钥
     *
     * @param issuerSecret 密钥
     */
    public void setIssuerSecret(String issuerSecret) {
        this.issuerSecret = issuerSecret;
    }

    /**
     * 入站校验的服务令牌清单
     *
     * @return 服务 ID 到令牌配置的映射
     */
    public Map<String, Issuer> getIssuers() {
        return issuers;
    }

    /**
     * 设置入站校验的服务令牌清单
     *
     * @param issuers 服务 ID 到令牌配置的映射
     */
    public void setIssuers(Map<String, Issuer> issuers) {
        this.issuers = issuers;
    }

    /**
     * 调用方服务令牌：密钥 + 允许访问的租户范围。
     */
    public static class Issuer {

        /** 服务密钥（请求头中 {@code <服务ID>.<密钥>} 的后半段） */
        private String secret = "";

        /** 允许访问的租户 ID 列表；非平台服务必须显式配置 */
        private List<String> tenants = new ArrayList<>();

        /** 是否平台级服务：为 {@code true} 时允许跨租户调用，不校验租户头 */
        private boolean platform;

        /**
         * 服务密钥
         *
         * @return 密钥（可能为空串）
         */
        public String getSecret() {
            return secret;
        }

        /**
         * 设置服务密钥
         *
         * @param secret 密钥
         */
        public void setSecret(String secret) {
            this.secret = secret;
        }

        /**
         * 允许访问的租户列表
         *
         * @return 租户 ID 列表
         */
        public List<String> getTenants() {
            return tenants;
        }

        /**
         * 设置允许访问的租户列表
         *
         * @param tenants 租户 ID 列表
         */
        public void setTenants(List<String> tenants) {
            this.tenants = tenants;
        }

        /**
         * 是否平台级服务（可跨租户）
         *
         * @return 平台级返回 {@code true}
         */
        public boolean isPlatform() {
            return platform;
        }

        /**
         * 设置是否平台级服务
         *
         * @param platform 平台级标记
         */
        public void setPlatform(boolean platform) {
            this.platform = platform;
        }
    }

    /**
     * 豁免路径模式列表
     *
     * @return Ant 模式列表
     */
    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * 设置豁免路径模式列表
     *
     * @param excludePatterns Ant 模式列表
     */
    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }
}
