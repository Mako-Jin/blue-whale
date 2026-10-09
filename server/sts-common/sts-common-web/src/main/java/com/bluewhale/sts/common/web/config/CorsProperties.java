package com.bluewhale.sts.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p>默认**关闭**：开发期的跨域由前端 dev server 的代理解决（同源请求，不触发 CORS），
 * 生产期由网关统一处理。把 CORS 默认打开，等于给每个服务都开了一个容易被遗忘的缺口，
 * 所以这里选择"要用才开"。
 *
 * <p><b>允许来源用 {@code allowedOriginPatterns} 而非 {@code allowedOrigins}：</b>
 * 后者不支持通配符与 {@code allowCredentials=true} 共存（浏览器规范不允许
 * {@code Access-Control-Allow-Origin: *} 搭配凭证），配错时表现为"请求发出去了，
 * 但响应里没有 CORS 头"，排查方向极易跑偏。
 */
@ConfigurationProperties(prefix = "bluewhale.web.cors")
public class CorsProperties {

    /** 是否启用跨域支持 */
    private boolean enabled = false;

    /** 允许的来源模式，支持通配符，如 {@code http://localhost:*} */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("http://localhost:*",
            "http://127.0.0.1:*"));

    /** 允许的方法 */
    private List<String> allowedMethods = new ArrayList<>(List.of("*"));

    /** 允许的请求头 */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /** 是否允许携带凭证（Cookie / Authorization） */
    private boolean allowCredentials = true;

    /** 预检结果缓存秒数 */
    private long maxAge = 3600L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

}
