package com.bluewhale.oa.attendance.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请实体，对应表 {@code oa_att_correction}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_att_correction")
public class AttendanceCorrectionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 补卡单号 */
    private String code;

    /** 用户ID */
    private Long userId;

    /** 用户姓名快照 */
    private String userName;

    /** 补卡日期 */
    private LocalDate workDate;

    /** 补卡时间 */
    private LocalDateTime punchTime;

    /** 打卡类型：IN / OUT */
    private String punchType;

    /** 补卡原因 */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED / CANCELLED */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 处理人账号 */
    private String handler;

    /** 处理时间 */
    private LocalDateTime handledTime;

    /** 备注 */
    private String remark;
}
