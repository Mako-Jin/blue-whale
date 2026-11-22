package com.bluewhale.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内网告警创建请求（供其他服务的规则动作调用）。
 *
 * <p>与 Prometheus 入站不同，本请求是「已判定触发」的语义：调用方负责条件评估与
 * 静默窗口去重，本接口只负责在目标租户下落一条 {@code FIRING} 记录。
 * {@code source} 仅用于接入审计日志与内部告警接入日志，不会写入记录内容。</p>
 *
 * @author BlueWhale
 */
@Data
public class AlertRecordCreateRequest {

    /** 来源标识（如 {@code iot}），仅用于接入审计日志 */
    @Size(max = 32, message = "{validation.alert.alert-record.source-size}")
    private String source;

    /** 规则编码（如 {@code iot:rule:123}） */
    @Size(max = 64, message = "{validation.alert.alert-record.rule-code-size}")
    private String ruleCode;

    /** 告警级别：INFO / WARN / ERROR / CRITICAL */
    @Size(max = 16, message = "{validation.alert.alert-record.alert-level-size}")
    private String alertLevel;

    /** 告警内容 */
    @NotBlank(message = "{validation.alert.alert-record.content-required}")
    @Size(max = 512, message = "{validation.alert.alert-record.content-size}")
    private String content;

    /** 告警指纹（同源同对象恒定，用于窗口内去重） */
    @Size(max = 64, message = "{validation.alert.alert-record.fingerprint-size}")
    private String fingerprint;

    /** 归属租户ID（可空，空时取当前上下文；必须为正整数） */
    private Long tenantId;

    /** 触发时间（可空，空时取服务端当前时间） */
    private LocalDateTime triggerTime;
}
