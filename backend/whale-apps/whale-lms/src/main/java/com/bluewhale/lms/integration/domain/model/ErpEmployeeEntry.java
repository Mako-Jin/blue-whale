package com.bluewhale.lms.integration.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERP 员工目录条目（内网只读契约的领域投影）。
 *
 * <p>只承载 LMS 真正会用到的字段。ERP 的员工视图还有工资、社保基数等敏感信息，
 * 拉进 LMS 既无用又多一处泄露面，因此内部契约刻意做窄。</p>
 *
 * @author BlueWhale
 */
@Data
public class ErpEmployeeEntry {

    /** 员工编号 */
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
    private String status;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /**
     * 转为快照。
     *
     * @param syncedAt 同步时刻
     * @return 快照
     */
    public MemberSnapshot toSnapshot(LocalDateTime syncedAt) {
        MemberSnapshot snapshot = new MemberSnapshot();
        snapshot.setEmployeeNo(employeeNo);
        snapshot.setUserId(userId);
        snapshot.setName(name);
        snapshot.setOrgCode(orgCode);
        snapshot.setPosition(position);
        snapshot.setStatus(MemberStatus.parse(status));
        snapshot.setHireDate(hireDate);
        snapshot.setLeaveDate(leaveDate);
        snapshot.setLastSyncedAt(syncedAt);
        return snapshot;
    }
}
