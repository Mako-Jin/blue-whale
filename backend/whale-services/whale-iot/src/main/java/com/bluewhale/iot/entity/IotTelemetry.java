package com.bluewhale.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IoT 遥测实体，对应表 {@code iot_telemetry}（按月分区）。
 *
 * <p>不继承 {@code BaseEntity}：分区表主键为 {@code (id, ts)}，且遥测是只追加的
 * 时序数据，无 {@code update_time}/{@code deleted} 语义。数值属性落 {@code value}，
 * 文本/枚举/事件载荷落 {@code value_text}，二者互斥。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("iot_telemetry")
public class IotTelemetry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法；分区表主键含 ts） */
    private Long id;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 设备ID（iot_device.id） */
    private Long deviceId;

    /** 属性标识符（物模型 property；事件为 {@code event:标识符}） */
    private String identifier;

    /** 数值型取值（非数值属性为 NULL） */
    private Double value;

    /** 文本型取值（数值属性为 NULL） */
    private String valueText;

    /** 遥测时间戳（分区键） */
    private LocalDateTime ts;
}
