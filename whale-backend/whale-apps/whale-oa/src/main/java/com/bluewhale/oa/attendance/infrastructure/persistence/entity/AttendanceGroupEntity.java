package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考勤组实体，对应表 {@code oa_att_group}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_group")
public class AttendanceGroupEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 考勤组编码 */
    private String code;

    /** 考勤组名称 */
    private String name;

    /** 班次ID */
    private Long shiftId;

    /** 规则ID */
    private Long ruleId;

    /** 打卡地点ID */
    private Long locationId;

    /** 打卡设备ID */
    private Long deviceId;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
