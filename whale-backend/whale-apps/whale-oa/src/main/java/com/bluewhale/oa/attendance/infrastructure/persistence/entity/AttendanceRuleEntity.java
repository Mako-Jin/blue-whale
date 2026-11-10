package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考勤规则实体，对应表 {@code oa_att_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_rule")
public class AttendanceRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String code;

    /** 规则名称 */
    private String name;

    /** 是否允许外勤 */
    private Integer allowExternal;

    /** 是否要求定位 */
    private Integer requireLocation;

    /** 是否要求设备打卡 */
    private Integer requireDevice;

    /** 每月补卡次数上限 */
    private Integer maxCorrectionsPerMonth;

    /** 补卡可追溯天数 */
    private Integer correctionWindowDays;

    /** 启停 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
