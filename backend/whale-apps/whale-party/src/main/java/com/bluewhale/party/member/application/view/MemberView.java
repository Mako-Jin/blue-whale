package com.bluewhale.party.member.application.view;

import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.model.PoliticalStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 党员档案视图。
 *
 * @author BlueWhale
 */
@Data
public class MemberView {

    /** 档案 ID */
    private Long id;

    /** 党员编号 */
    private String memberNo;

    /** 员工编号 */
    private String employeeNo;

    /** 平台用户 ID */
    private Long userId;

    /** 党员姓名 */
    private String memberName;

    /** 所属支部 ID */
    private Long branchId;

    /** 所属支部名称 */
    private String branchName;

    /** 政治面貌码 */
    private String politicalStatus;

    /** 政治面貌标签 */
    private String politicalLabel;

    /** 入党日期 */
    private LocalDate joinPartyDate;

    /** 转正日期 */
    private LocalDate fullMemberDate;

    /** 党龄（整年） */
    private Long partyAgeYears;

    /** 是否待转正 */
    private boolean probationaryPending;

    /** 党费基数 */
    private BigDecimal duesBase;

    /** 联系电话 */
    private String phone;

    /** 状态码 */
    private String status;

    /** 状态标签 */
    private String statusLabel;

    /** 组织关系转出日期 */
    private LocalDate transferOutDate;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param member 党员档案
     * @return 视图
     */
    public static MemberView from(PartyMember member) {
        MemberView view = new MemberView();
        view.setId(member.getId());
        view.setMemberNo(member.getMemberNo());
        view.setEmployeeNo(member.getEmployeeNo());
        view.setUserId(member.getUserId());
        view.setMemberName(member.getMemberName());
        view.setBranchId(member.getBranchId());
        view.setJoinPartyDate(member.getJoinPartyDate());
        view.setFullMemberDate(member.getFullMemberDate());
        view.setPartyAgeYears(member.partyAgeYears(LocalDate.now()));
        view.setProbationaryPending(member.isProbationaryPending());
        view.setDuesBase(member.getDuesBase());
        view.setPhone(member.getPhone());
        view.setTransferOutDate(member.getTransferOutDate());
        view.setRemark(member.getRemark());
        view.setCreateTime(member.getCreateTime());
        PoliticalStatus political = member.getPoliticalStatus() == null
                ? PoliticalStatus.FULL_MEMBER : member.getPoliticalStatus();
        view.setPoliticalStatus(political.name());
        view.setPoliticalLabel(political.getLabel());
        MemberStatus status = member.getStatus() == null ? MemberStatus.SUSPENDED : member.getStatus();
        view.setStatus(status.name());
        view.setStatusLabel(status.getLabel());
        return view;
    }
}
