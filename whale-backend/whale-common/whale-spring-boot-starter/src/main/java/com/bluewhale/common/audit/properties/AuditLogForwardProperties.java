package com.bluewhale.common.audit.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计日志转发配置（{@code whale.audit.forward.*}）。
 *
 * <p>默认关闭：未显式开启时，审计记录仍按原逻辑落到各服务自己的 {@code audit_log}，
 * 行为与引入本能力前完全一致。开启后审计切面优先把记录转发到 whale-audit，
 * 转发失败自动回退本地落库，保证不丢审计。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.audit.forward")
public class AuditLogForwardProperties {

    /** 是否开启转发（默认关闭） */
    private boolean enabled = false;

    /** whale-audit 基址（不含路径），转发目标为 {@code {url}/audit/log/ingest} */
    private String url = "http://127.0.0.1:9101";

    /** 连接与读取超时（毫秒）：超时即回退本地落库 */
    private int timeoutMillis = 800;

    /**
     * 转发失败是否回退本地落库（默认 {@code true}，保证不丢审计）。
     * <p>本地已不建 {@code audit_log} 表的服务（审计由 whale-audit 多实例兜底）应显式关闭，
     * 避免 fallback insert 持续报错。</p>
     */
    private boolean localFallback = true;
}
