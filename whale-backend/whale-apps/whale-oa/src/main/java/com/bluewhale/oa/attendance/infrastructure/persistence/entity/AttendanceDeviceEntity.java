package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡设备实体，对应表 {@code oa_att_device}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_device")
public class AttendanceDeviceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 设备编码 */
    private String code;

    /** 设备名称 */
    private String name;

    /** 设备类型 */
    private String deviceType;

    /** 打卡地点ID */
    private Long locationId;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
