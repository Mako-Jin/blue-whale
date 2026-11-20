package com.bluewhale.alert.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警记录视图对象（字段与 {@code alert_record} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class AlertRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 告警规则ID */
    private Long ruleId;

    /** 告警规则编码 */
    private String ruleCode;

    /** 告警级别 */
    private String alertLevel;

    /** 告警内容描述 */
    private String content;

    /** 记录状态：FIRING 触发中 / RECOVERED 已恢复 */
    private String status;

    /** 告警指纹（用于排查窗口内去重） */
    private String fingerprint;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 恢复时间 */
    private LocalDateTime recoverTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
