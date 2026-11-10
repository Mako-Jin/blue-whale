package com.bluewhale.party.member.domain.model;

import lombok.Data;

/**
 * 党员档案查询条件。
 *
 * @author BlueWhale
 */
@Data
public class PartyMemberQuery {

    /** 关键字（党员编号 / 工号 / 姓名模糊） */
    private String keyword;

    /** 所属支部 ID */
    private Long branchId;

    /** 政治面貌 */
    private String politicalStatus;

    /** 档案状态 */
    private String status;

    /** 是否只看待转正（预备党员且未填转正日期） */
    private Boolean probationaryPending;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
