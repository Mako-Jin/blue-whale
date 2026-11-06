package com.bluewhale.party.union.domain.model;

import lombok.Data;

/**
 * 工会活动查询条件。
 *
 * @author BlueWhale
 */
@Data
public class UnionActivityQuery {

    /** 关键字（活动编号 / 名称） */
    private String keyword;

    /** 活动状态 */
    private String status;
}
