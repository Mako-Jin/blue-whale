package com.bluewhale.alert.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警静默视图对象。
 *
 * @author BlueWhale
 */
@Data
public class AlertSilenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 规则编码，空表示全部规则 */
    private String ruleCode;

    /** 告警级别，空表示全部级别 */
    private String alertLevel;

    /** 内容关键字，空表示全部内容 */
    private String keyword;

    /** 静默开始时间 */
    private LocalDateTime startTime;

    /** 静默结束时间 */
    private LocalDateTime endTime;

    /** 静默原因 */
    private String reason;

    /** 创建人ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
