package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleVO {

    /** 规则ID（字符串化） */
    private String id;

    /** 规则名称 */
    private String name;

    /** 限定产品ID（可空，字符串化） */
    private String productId;

    /** 限定产品名称 */
    private String productName;

    /** 限定设备ID（可空，字符串化） */
    private String deviceId;

    /** 限定设备名称 */
    private String deviceName;

    /** 触发条件 */
    private RuleCondition condition;

    /** 动作类型 */
    private String actionType;

    /** 动作配置 */
    private RuleActionConfig actionConfig;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;

    /** 最近触发时间（ISO-8601，可空） */
    private String lastFiredTime;

    /** 累计触发次数 */
    private Long firedCount;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
