package com.bluewhale.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 告警静默规则实体，对应表 {@code alert_silence}。
 *
 * <p>matcher 语义：{@code ruleCode} / {@code alertLevel} / {@code keyword} 均为空表示匹配全部；
 * 非空时分别按规则编码、级别精确匹配，{@code keyword} 按内容包含匹配。
 * 命中静默的告警落库为 {@code SILENCED} 状态且不发送通知。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_silence")
public class AlertSilence extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码（空表示全部规则） */
    private String ruleCode;

    /** 告警级别（空表示全部级别） */
    private String alertLevel;

    /** 内容关键字（空表示全部内容） */
    private String keyword;

    /** 静默开始时间 */
    private LocalDateTime startTime;

    /** 静默结束时间 */
    private LocalDateTime endTime;

    /** 静默原因 */
    private String reason;

    /** 创建人ID */
    private String createdBy;
}
