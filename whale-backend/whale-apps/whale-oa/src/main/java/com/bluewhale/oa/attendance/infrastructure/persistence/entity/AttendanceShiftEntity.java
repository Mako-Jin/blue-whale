package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

/**
 * 考勤班次实体，对应表 {@code oa_att_shift}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_shift")
public class AttendanceShiftEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 班次编码 */
    private String code;

    /** 班次名称 */
    private String name;

    /** 上班时间 */
    private LocalTime workStart;

    /** 下班时间 */
    private LocalTime workEnd;

    /** 弹性分钟 */
    private Integer flexMinutes;

    /** 休息时长（分钟） */
    private Integer breakMinutes;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
