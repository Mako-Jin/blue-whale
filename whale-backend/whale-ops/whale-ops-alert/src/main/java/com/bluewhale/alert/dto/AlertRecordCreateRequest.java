package com.bluewhale.alert.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内网告警创建请求（供其他服务的规则动作调用）。
 *
 * <p>与 Prometheus 入站不同，本请求是「已判定触发」的语义：调用方负责条件评估与
 * 静默期去重，本接口只负责在当前租户下落一条 {@code FIRING} 记录。</p>
 *
 * @author BlueWhale
 */
@Data
public class AlertRecordCreateRequest {

    /** 来源标识（如 {@code iot}，写入内容前缀便于检索） */
    private String source;

    /** 规则编码（如 {@code iot:rule:123}） */
    private String ruleCode;

    /** 告警级别：INFO / WARN / ERROR / CRITICAL */
    private String alertLevel;

    /** 告警内容 */
    private String content;

    /** 告警指纹（同源同对象恒定，用于窗口内去重） */
    private String fingerprint;

    /** 归属租户ID（可空，空时取当前上下文） */
    private Long tenantId;

    /** 触发时间（可空，空时取服务端当前时间） */
    private LocalDateTime triggerTime;
}
