package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单条遥测数据点（服务内部与设备上报解析后的统一结构）。
 *
 * <p>数值型属性只填 {@code value}，文本/枚举/事件载荷填 {@code valueText}，
 * 二者互斥由 {@code IotTelemetryService} 按物模型数据类型分流。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryPoint {

    /** 属性标识符（事件为 {@code event:标识符}） */
    private String identifier;

    /** 数值型取值 */
    private Double value;

    /** 文本型取值 */
    private String valueText;

    /** 数据时间戳（缺省取服务端接收时间） */
    private LocalDateTime ts;
}
