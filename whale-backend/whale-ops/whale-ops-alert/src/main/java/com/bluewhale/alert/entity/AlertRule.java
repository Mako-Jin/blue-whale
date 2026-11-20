package com.bluewhale.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 告警规则实体，对应表 {@code alert_rule}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code alert_rule} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    private String ruleName;

    /** 规则编码（业务唯一；因逻辑删除不建唯一索引，唯一性由服务层前置校验保证） */
    private String ruleCode;

    /** 监控指标，如 cpu.usage */
    private String metric;

    /** 监控目标（服务名 / 实例 / 资源标识） */
    private String target;

    /** 比较运算符：GT / GE / LT / LE / EQ / NE */
    private String operator;

    /** 触发阈值 */
    private BigDecimal threshold;

    /** 持续时长（秒），持续超过阈值达到该时长才触发 */
    private Integer durationSeconds;

    /** 告警级别：INFO / WARN / ERROR / CRITICAL */
    private String level;

    /** 通知通道：EMAIL / SMS / WEBHOOK / NONE */
    private String channel;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
