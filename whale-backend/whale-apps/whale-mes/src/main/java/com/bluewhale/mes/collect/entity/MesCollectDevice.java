package com.bluewhale.mes.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集设备（网关/边缘节点）实体，对应表 {@code mes_cl_device}。
 *
 * <p>MES 不直连 PLC：现场由网关以 REST/MQTT 方式推送样本，设备只做注册与在线状态维护。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_cl_device")
public class MesCollectDevice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 采集设备编码（租户内唯一） */
    private String deviceCode;

    /** 采集设备名称 */
    private String deviceName;

    /** 类型：GATEWAY / EDGE / SENSOR */
    private String deviceType;

    /** 协议：REST / MQTT / OPCUA */
    private String protocol;

    /** 接入地址 */
    private String ipAddress;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码快照 */
    private String lineCode;

    /** 状态：ONLINE / OFFLINE / DISABLED */
    private String status;

    /** 最近心跳时间 */
    private LocalDateTime lastHeartbeatAt;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
