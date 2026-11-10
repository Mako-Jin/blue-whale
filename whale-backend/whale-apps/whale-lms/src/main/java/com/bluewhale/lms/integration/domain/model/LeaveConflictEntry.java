package com.bluewhale.lms.integration.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 请假条目（OA 内网契约的领域投影）。
 *
 * @author BlueWhale
 */
@Data
public class LeaveConflictEntry {

    /** 请假单号 */
    private String code;

    /** 申请人用户 ID */
    private Long applicantId;

    /** 申请人账号 */
    private String applicant;

    /** 假期类型编码 */
    private String leaveType;

    /** 开始日期（含） */
    private LocalDate startDate;

    /** 结束日期（含） */
    private LocalDate endDate;

    /** 申请状态 */
    private String status;

    /**
     * 是否覆盖指定日期。
     *
     * @param day 日期
     * @return 覆盖返回 {@code true}
     */
    public boolean covers(LocalDate day) {
        if (day == null || startDate == null || endDate == null) {
            return false;
        }
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }
}
