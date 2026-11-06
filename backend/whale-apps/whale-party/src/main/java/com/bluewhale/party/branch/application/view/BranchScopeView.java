package com.bluewhale.party.branch.application.view;

import lombok.Data;

/**
 * 支部统计范围（看板刷新时逐支部计算用）。
 *
 * @author BlueWhale
 */
@Data
public class BranchScopeView {

    /** 支部 ID */
    private Long branchId;

    /** 支部名称（作为快照的范围名称） */
    private String branchName;
}
