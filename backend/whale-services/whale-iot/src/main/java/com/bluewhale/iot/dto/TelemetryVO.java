package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 遥测数据点视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryVO {

    /** 属性标识符 */
    private String identifier;

    /** 数值型取值（非数值属性为空） */
    private Double value;

    /** 文本型取值（数值属性为空） */
    private String valueText;

    /** 数据时间（ISO-8601） */
    private String ts;
}
