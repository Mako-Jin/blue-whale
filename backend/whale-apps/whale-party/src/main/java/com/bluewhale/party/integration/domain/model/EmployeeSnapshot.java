package com.bluewhale.party.integration.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工只读快照（覆盖率分母来源）。
 *
 * <p>离职员工保留快照而不是删除：历史台账要能追溯「当时是谁」。
 * 覆盖率只统计在职员工，是否在职由 {@link #isActive()} 判断。</p>
 *
 * @author BlueWhale
 */
@Data
public class EmployeeSnapshot {

    /** 主键 */
    private Long id;

    /** 员工编号（ERP 主键口径） */
    private String employeeNo;

    /** 平台账号 ID（未开通为空） */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 在职状态：ACTIVE / LEFT */
    private String status = "ACTIVE";

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 最近同步时间 */
    private LocalDateTime lastSyncedAt;

    /**
     * 是否在职。
     *
     * @return 在职返回 true
     */
    public boolean isActive() {
        return status == null || "ACTIVE".equalsIgnoreCase(status.trim());
    }
}
