package com.bluewhale.alert.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警规则视图对象（字段与 {@code alert_rule} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class AlertRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则编码 */
    private String ruleCode;

    /** 监控指标 */
    private String metric;

    /** 监控目标 */
    private String target;

    /** 比较运算符 */
    private String operator;

    /** 触发阈值 */
    private BigDecimal threshold;

    /** 持续时长（秒） */
    private Integer durationSeconds;

    /** 告警级别 */
    private String level;

    /** 通知通道 */
    private String channel;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
