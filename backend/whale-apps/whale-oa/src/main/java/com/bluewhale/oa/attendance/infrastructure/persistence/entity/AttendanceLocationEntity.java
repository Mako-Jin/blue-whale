package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 打卡地点实体，对应表 {@code oa_att_location}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_location")
public class AttendanceLocationEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 地点编码 */
    private String code;

    /** 地点名称 */
    private String name;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 允许半径（米） */
    private Integer radiusMeters;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
