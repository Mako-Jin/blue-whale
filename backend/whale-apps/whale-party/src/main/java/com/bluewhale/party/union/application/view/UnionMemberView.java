package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工会会员视图。
 *
 * @author BlueWhale
 */
@Data
public class UnionMemberView {

    /** 会员 ID */
    private Long id;

    /** 会员编号 */
    private String memberNo;

    /** 员工编号 */
    private String employeeNo;

    /** 平台用户 ID */
    private Long userId;

    /** 会员姓名 */
    private String memberName;

    /** 所属部门名称 */
    private String deptName;

    /** 入会日期 */
    private LocalDate joinDate;

    /** 会籍状态码 */
    private String status;

    /** 退会日期 */
    private LocalDate withdrawDate;

    /** 会费基数 */
    private BigDecimal duesBase;

    /** 会费缴至月份 */
    private String duesPaidTo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param member 工会会员
     * @return 视图
     */
    public static UnionMemberView from(UnionMember member) {
        UnionMemberView view = new UnionMemberView();
        view.setId(member.getId());
        view.setMemberNo(member.getMemberNo());
        view.setEmployeeNo(member.getEmployeeNo());
        view.setUserId(member.getUserId());
        view.setMemberName(member.getMemberName());
        view.setDeptName(member.getDeptName());
        view.setJoinDate(member.getJoinDate());
        UnionMemberStatus status = member.getStatus() == null ? UnionMemberStatus.ACTIVE : member.getStatus();
        view.setStatus(status.name());
        view.setWithdrawDate(member.getWithdrawDate());
        view.setDuesBase(member.getDuesBase());
        view.setDuesPaidTo(member.getDuesPaidTo());
        view.setRemark(member.getRemark());
        view.setCreateTime(member.getCreateTime());
        return view;
    }
}
