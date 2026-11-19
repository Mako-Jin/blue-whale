package com.bluewhale.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审计日志导出/归档配置（AUTH-08）。
 *
 * <p>{@code whale.audit.export.*}：whale-file 内网地址、归档目标桶与单次导出行数上限。
 * 导出接口 {@code POST /audit/log/export} 与保留期归档任务 {@code AuditLogArchiver} 共用本配置。</p>
 *
 * <p>本类以 {@code @Component} 注册为普通 Bean（与 {@code RetentionProperties} 一致），
 * 依赖 Spring 对 {@code @Component + @ConfigurationProperties} 的绑定处理，无需启动类显式
 * {@code @ConfigurationPropertiesScan}。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.audit.export")
public class AuditExportProperties {

    /** whale-file 服务基址（服务间直连，不经网关），默认本机 8085 */
    private String fileServiceUrl = "http://127.0.0.1:8085";

    /** 归档目标桶名称，默认 bluewhale-audit-archive（与史上传桶隔离） */
    private String bucket = "bluewhale-audit-archive";

    /** 单次导出/归档的行数上限，超限仅取上限行并告警，默认 50000 */
    private int maxRows = 50000;

    /** 归档单批行数（分页流式，每批独立上传并推进水位），默认 1000 */
    private int batchRows = 1000;
}
