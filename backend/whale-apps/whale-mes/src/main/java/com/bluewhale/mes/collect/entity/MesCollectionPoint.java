package com.bluewhale.mes.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采集点实体，对应表 {@code mes_cl_collection_point}。
 *
 * <p>采集点编码租户内唯一；绑定采集设备与设备/工序，网关以编码定位上报目标。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_cl_collection_point")
public class MesCollectionPoint extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 采集点编码（租户内唯一） */
    private String pointCode;

    /** 采集点名称 */
    private String pointName;

    /** 采集设备 ID */
    private Long deviceId;

    /** 采集设备编码快照 */
    private String deviceCode;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 工序编码 */
    private String operationCode;

    /** 指标类型：STATUS / COUNT / TEMPERATURE / PRESSURE / SPEED / METRIC */
    private String metricType;

    /** 计量单位 */
    private String unit;

    /** 采集间隔（秒） */
    private Integer collectIntervalSeconds;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
