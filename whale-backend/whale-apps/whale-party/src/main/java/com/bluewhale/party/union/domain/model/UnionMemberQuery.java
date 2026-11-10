package com.bluewhale.party.union.domain.model;

import lombok.Data;

/**
 * 工会会员查询条件。
 *
 * @author BlueWhale
 */
@Data
public class UnionMemberQuery {

    /** 关键字（会员编号 / 员工编号 / 姓名） */
    private String keyword;

    /** 会籍状态 */
    private String status;
}
