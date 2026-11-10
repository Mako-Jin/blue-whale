package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 绑定阈值告警视图（运行时级别变化产物，SSE {@code alert} 帧载荷）。
 *
 * <p>只在级别变化时推送：首评越界（previous 为空）、升级、降级与恢复；
 * 离线不做恢复语义（上下线由 {@code binding} 帧表达）。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindingAlertVO {

    /** 绑定ID（字符串化） */
    private String bindingId;

    /** 孪生实体ID（字符串化） */
    private String entityId;

    /** 实体编码 */
    private String entityCode;

    /** 实体状态键 */
    private String stateKey;

    /** 触发取值 */
    private Object value;

    /** 上一级别（首评越界时为空） */
    private String previous;

    /** 当前级别：normal / warn / critical */
    private String level;

    /** 参与判定的阈值（升序） */
    private List<BigDecimal> thresholds;

    /** 评估时间（ISO-8601） */
    private String ts;
}
