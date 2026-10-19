package com.bluewhale.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志保留期配置（REQ-SEC-006）。
 *
 * <p>{@code whale.audit.retention.*}：是否启用清理 / 保留天数 / 清理调度 cron。
 * <b>默认 {@code enabled=false}</b>，须显式开启才会清理，避免误删；
 * 清理范围仅 4 张日志表（{@code api_log}/{@code outbound_log}/{@code frontend_log}/{@code behavior_log}），
 * {@code audit_log} 的保留/归档策略另属 AUTH-09，不在本配置范围内。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.audit.retention")
public class RetentionProperties {

    /** 是否启用日志保留期清理，默认关闭（须显式开启，避免误删） */
    private boolean enabled = false;

    /** 保留天数：早于「当前时间 - 本天数」的记录将被清理，默认 30 */
    private int days = 30;

    /** 清理调度 cron（Spring 6 段式），默认每日 03:00 */
    private String cron = "0 0 3 * * ?";

    /**
     * 是否随保留期调度执行 {@code audit_log} 归档导出（AUTH-09），默认关闭（须显式开启）。
     *
     * <p>开启后仅「导出归档并上传 whale-file」，<b>绝不对 {@code audit_log} 做物理/逻辑删除</b>；
     * 须 {@code whale.audit.retention.enabled=true} 装配调度器后本开关才生效。</p>
     */
    private boolean archiveEnabled = false;
}
