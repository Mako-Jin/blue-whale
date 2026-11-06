package com.bluewhale.party.branch.application.view;

import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支部视图。
 *
 * @author BlueWhale
 */
@Data
public class BranchView {

    /** 支部 ID */
    private Long id;

    /** 支部编码 */
    private String branchCode;

    /** 支部名称 */
    private String branchName;

    /** 上级支部 ID */
    private Long parentId;

    /** 上级支部名称（列表展示用，一级支部为空） */
    private String parentName;

    /** 支部层级 */
    private Integer branchLevel;

    /** 关联 ERP 组织编码 */
    private String orgCode;

    /** 支部书记姓名 */
    private String secretaryName;

    /** 支部书记用户 ID */
    private Long secretaryUserId;

    /** 成立日期 */
    private LocalDate establishedDate;

    /** 状态码 */
    private String status;

    /** 状态标签 */
    private String statusLabel;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param branch 支部
     * @return 视图
     */
    public static BranchView from(Branch branch) {
        BranchView view = new BranchView();
        view.setId(branch.getId());
        view.setBranchCode(branch.getBranchCode());
        view.setBranchName(branch.getBranchName());
        view.setParentId(branch.getParentId());
        view.setBranchLevel(branch.getBranchLevel());
        view.setOrgCode(branch.getOrgCode());
        view.setSecretaryName(branch.getSecretaryName());
        view.setSecretaryUserId(branch.getSecretaryUserId());
        view.setEstablishedDate(branch.getEstablishedDate());
        BranchStatus status = branch.getStatus() == null ? BranchStatus.SUSPENDED : branch.getStatus();
        view.setStatus(status.name());
        view.setStatusLabel(status.getLabel());
        view.setSortNo(branch.getSortNo());
        view.setRemark(branch.getRemark());
        view.setCreateTime(branch.getCreateTime());
        return view;
    }
}
