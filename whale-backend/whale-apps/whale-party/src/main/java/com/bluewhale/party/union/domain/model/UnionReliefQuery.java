package com.bluewhale.party.union.domain.model;

import lombok.Data;

/**
 * 慰问帮扶查询条件。
 *
 * @author BlueWhale
 */
@Data
public class UnionReliefQuery {

    /** 关键字（申请编号 / 申请事由） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 慰问类型 */
    private String reliefType;

    /** 申请年度 */
    private Integer year;
}
