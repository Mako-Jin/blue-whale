package com.bluewhale.oa.attendance.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请。
 *
 * @author BlueWhale
 */
@Data
public class AttendanceCorrection {

    private Long id;

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

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 处理人账号 */
    private String handler;

    /** 处理时间 */
    private LocalDateTime handledTime;

    /** 备注 */
    private String remark;

    /**
     * 判断是否允许撤销。
     *
     * @return 是否可撤销
     */
    public boolean cancellable() {
        return AttendanceStatus.CORRECTION_PENDING.equals(status);
    }
}
