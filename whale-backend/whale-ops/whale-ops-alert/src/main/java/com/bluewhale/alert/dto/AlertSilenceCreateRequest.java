package com.bluewhale.alert.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警静默创建请求。
 *
 * <p>matcher 三项均可空：为空表示"匹配全部"；非空时分别按规则编码、级别精确匹配，
 * {@code keyword} 按告警内容包含匹配。</p>
 *
 * @author BlueWhale
 */
@Data
public class AlertSilenceCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码，空表示全部规则 */
    @Size(max = 64, message = "{validation.alert.alert-silence.rule-code-size}")
    private String ruleCode;

    /** 告警级别，空表示全部级别 */
    @Size(max = 16, message = "{validation.alert.alert-silence.alert-level-size}")
    private String alertLevel;

    /** 内容关键字，空表示全部内容 */
    @Size(max = 128, message = "{validation.alert.alert-silence.keyword-size}")
    private String keyword;

    /** 静默开始时间 */
    @NotNull(message = "{validation.alert.alert-silence.start-time-required}")
    private LocalDateTime startTime;

    /** 静默结束时间 */
    @NotNull(message = "{validation.alert.alert-silence.end-time-required}")
    private LocalDateTime endTime;

    /** 静默原因 */
    @Size(max = 255, message = "{validation.alert.alert-silence.reason-size}")
    private String reason;

    /** 创建人ID */
    @Size(max = 64, message = "{validation.alert.alert-silence.created-by-size}")
    private String createdBy;
}
