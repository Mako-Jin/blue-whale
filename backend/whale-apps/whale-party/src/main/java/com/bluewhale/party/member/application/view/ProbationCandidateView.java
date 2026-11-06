package com.bluewhale.party.member.application.view;

import lombok.Data;

import java.time.LocalDate;

/**
 * 预备党员转正提醒候选（M-P4）。
 *
 * @author BlueWhale
 */
@Data
public class ProbationCandidateView {

    /** 党员档案 ID */
    private Long memberId;

    /** 党员姓名 */
    private String memberName;

    /** 党员本人用户 ID（未开通账号为空） */
    private Long userId;

    /** 所属支部 ID */
    private Long branchId;

    /** 所属支部名称 */
    private String branchName;

    /** 支部书记用户 ID（未开通账号为空） */
    private Long secretaryUserId;

    /** 入党日期 */
    private LocalDate joinPartyDate;

    /** 应转正日期（入党日期满一年） */
    private LocalDate dueDate;
}
