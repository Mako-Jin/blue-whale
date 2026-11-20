package com.bluewhale.common.internaltoken;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务侧内网接口令牌校验配置（V-02 纵深防御）。
 *
 * <p>网关侧 {@code internal-deny} 只能阻止「经网关」的外部访问；直连业务端口的
 * {@code /internal/**} 请求由本组件在服务侧校验共享令牌（请求头 {@code X-Internal-Token}）。</p>
 *
 * <p>令牌解析顺序：显式配置 {@code bluewhale.internal-token.token} 优先，为空时回退环境变量
 * {@code WHALE_INTERNAL_TOKEN}；两者均为空时：dev 环境仅告警放行，非 dev 环境
 * {@link InternalTokenGuard} 拒绝启动（fail-fast，与 whale-alert WebhookTokenGuard 同策略）。</p>
 *
 * @author BlueWhale
 */
@ConfigurationProperties(prefix = "bluewhale.internal-token")
public class InternalTokenProperties {

    /** 默认令牌请求头名称 */
    private static final String DEFAULT_HEADER = "X-Internal-Token";

    /** 令牌环境变量名 */
    private static final String TOKEN_ENV = "WHALE_INTERNAL_TOKEN";

    /** 是否启用服务侧内网接口令牌校验 */
    private boolean enabled = true;

    /** 令牌请求头名称 */
    private String header = DEFAULT_HEADER;

    /** 共享内网令牌；为空时回退读取环境变量 {@code WHALE_INTERNAL_TOKEN} */
    private String token = "";

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
