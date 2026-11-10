package com.bluewhale.lms.integration.application.view;

import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工快照视图。
 *
 * @author BlueWhale
 */
@Data
public class MemberSnapshotView {

    /** 主键 */
    private Long id;

    /** 员工编号 */
    private String employeeNo;

    /** 平台账号 ID */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 在职状态 */
    private String status;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 最近同步时间 */
    private LocalDateTime lastSyncedAt;

    /** 是否可被催办（在职且已开通账号） */
    private boolean urgeable;

    /**
     * 由领域对象构建视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    public static MemberSnapshotView of(MemberSnapshot snapshot) {
        MemberSnapshotView view = new MemberSnapshotView();
        view.setId(snapshot.getId());
        view.setEmployeeNo(snapshot.getEmployeeNo());
        view.setUserId(snapshot.getUserId());
        view.setName(snapshot.getName());
        view.setOrgCode(snapshot.getOrgCode());
        view.setPosition(snapshot.getPosition());
        view.setStatus(snapshot.getStatus() == null ? null : snapshot.getStatus().name());
        view.setHireDate(snapshot.getHireDate());
        view.setLeaveDate(snapshot.getLeaveDate());
        view.setLastSyncedAt(snapshot.getLastSyncedAt());
        view.setUrgeable(snapshot.isUrgeable());
        return view;
    }
}
