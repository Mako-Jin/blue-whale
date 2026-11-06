package com.bluewhale.party.branch.application.view;

import lombok.Data;

import java.time.LocalDate;

/**
 * 换届到期支部（M-P4，M-P1 遗留收口）。
 *
 * @author BlueWhale
 */
@Data
public class BranchTermDueView {

    /** 支部 ID */
    private Long branchId;

    /** 支部名称 */
    private String branchName;

    /** 成立日期 */
    private LocalDate establishedDate;

    /** 应换届日期（成立日期 + 届期） */
    private LocalDate termDate;

    /** 距应换届日期剩余天数（负数表示已逾期） */
    private long remainingDays;

    /** 支部书记姓名 */
    private String secretaryName;

    /** 支部书记用户 ID（未开通账号为空） */
    private Long secretaryUserId;
}
