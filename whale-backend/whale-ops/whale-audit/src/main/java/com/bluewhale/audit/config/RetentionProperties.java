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
     * 是否随保留期调度执行 {@code audit_log} 归档导出（AUTH-09），<b>默认开启</b>（可显式关闭）。
     *
     * <p>开启后仅「导出归档并上传 whale-file」，<b>绝不对 {@code audit_log} 做物理/逻辑删除</b>；
     * 归档与清理解耦：即使 {@code whale.audit.retention.enabled=false}（不清理 4 张日志表），
     * 本开关为 {@code true} 时调度器仍会装配并只执行归档。</p>
     */
    private boolean archiveEnabled = true;

    /**
     * 审计日志要求的最少留存天数（等保 2.0 8.1.4.3 a：不少于 6 个月），默认 180。
     *
     * <p>仅用于留存巡检告警阈值，<b>不参与任何删除决策</b>——{@code audit_log} 永不删除，
     * 本条只决定「最早记录距今不足多少天时报警」。</p>
     */
    private int minimumDays = 180;
}
