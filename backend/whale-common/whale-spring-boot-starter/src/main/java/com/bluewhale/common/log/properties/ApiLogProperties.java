package com.bluewhale.common.log.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 后端接口日志配置（REQ-SEC-006，{@code ApiLogFilter}）。
 *
 * <p>{@code whale.log.api.*}：开关 / 采样率 / body 缓存上限 / 排除路径。
 * {@code exclude-paths} 默认含上报接口（{@code /audit/log/frontend/**}、
 * {@code /audit/log/behavior/**}）以防上报请求本身被记为接口日志形成回环。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.log.api")
public class ApiLogProperties {

    /** 默认排除路径：探活、静态资源、错误页与前端上报接口（防回环） */
    private static final List<String> DEFAULT_EXCLUDE_PATHS = List.of(
            "/actuator/**", "/health", "/favicon.ico", "/error",
            "/audit/log/frontend/**", "/audit/log/behavior/**");

    /** 接口日志开关 */
    private boolean enabled = true;

    /** 采样率 [0,1]：0 表示全不采集，1 表示全采集 */
    private double sampleRate = 1.0D;

    /** 单请求/响应体缓存上限（字符），超限停止缓存后续内容 */
    private int maxBodyLength = 8192;

    /** 排除路径（Ant 风格），命中则不打接口日志 */
    private List<String> excludePaths = new ArrayList<>(DEFAULT_EXCLUDE_PATHS);
}
