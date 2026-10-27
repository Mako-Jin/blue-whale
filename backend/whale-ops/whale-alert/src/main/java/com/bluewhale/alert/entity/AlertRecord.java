package com.bluewhale.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 告警记录实体，对应表 {@code alert_record}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code alert_record} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_record")
public class AlertRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 告警规则ID（逻辑关联 alert_rule.id） */
    private Long ruleId;

    /** 告警规则编码（冗余，便于记录独立查询） */
    private String ruleCode;

    /** 告警级别：INFO / WARN / ERROR / CRITICAL */
    private String alertLevel;

    /** 告警内容描述 */
    private String content;

    /** 记录状态：FIRING 触发中 / RECOVERED 已恢复 */
    private String status;

    /** 告警指纹：Alertmanager fingerprint 或按标签计算的 SHA-256（用于窗口内去重） */
    private String fingerprint;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 恢复时间（未恢复为 {@code null}） */
    private LocalDateTime recoverTime;
}
