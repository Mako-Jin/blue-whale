package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阈值告警视图（历史查询与未恢复列表接口返回）。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinAlertVO {

    /** 告警ID（字符串化） */
    private String id;

    /** 孪生实体ID（字符串化，可空） */
    private String entityId;

    /** 实体编码 */
    private String entityCode;

    /** 绑定ID（字符串化） */
    private String bindingId;

    /** 实体状态键 */
    private String stateKey;

    /** 触发取值（字符串化） */
    private String value;

    /** 上一级别（首评为空） */
    private String previousLevel;

    /** 当前级别：normal / warn / critical */
    private String level;

    /** 参与判定的阈值（升序） */
    private List<BigDecimal> thresholds;

    /** 是否已恢复：true = 恢复到 normal */
    private Boolean resolved;

    /** 恢复时间（ISO-8601，未恢复为空） */
    private String resolveTime;

    /** 触发时间（ISO-8601） */
    private String createTime;

    /** 是否已确认（人工知悉，与恢复语义独立） */
    private Boolean acked;

    /** 确认时间（ISO-8601，未确认为空） */
    private String ackTime;

    /** 确认人用户ID（字符串化，未确认为空） */
    private String ackBy;
}
