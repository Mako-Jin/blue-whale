package com.bluewhale.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IoT 规则实体，对应表 {@code iot_rule}。
 *
 * <p>规则 = 触发条件 + 动作。条件以 JSON 存 {@code condition_json}
 * （{@code identifier/op/value/durationSec}），动作以 JSON 存 {@code action_config}
 * （告警级别、静默期、通知渠道）。首期动作只实现 {@code alert}：命中后调
 * whale-alert 内网接口落一条告警记录；{@code webhook} 动作仅保留配置位。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_rule")
public class IotRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    private String name;

    /** 限定产品ID（{@code null} 表示全部产品） */
    private Long productId;

    /** 限定设备ID（{@code null} 表示产品下全部设备） */
    private Long deviceId;

    /** 触发条件 JSON */
    private String conditionJson;

    /** 动作类型：alert 告警 / webhook 回调 */
    private String actionType;

    /** 动作配置 JSON（告警级别 / 静默期 / 通知渠道） */
    private String actionConfig;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;

    /** 最近触发时间（限流去重用） */
    private LocalDateTime lastFiredTime;

    /** 累计触发次数 */
    private Long firedCount;
}
