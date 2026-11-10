package com.bluewhale.party.union.domain.model;

import lombok.Data;

/**
 * 节日福利批次查询条件。
 *
 * @author BlueWhale
 */
@Data
public class UnionWelfareQuery {

    /** 关键字（批次编号 / 福利名称） */
    private String keyword;

    /** 福利年度 */
    private Integer year;

    /** 批次状态 */
    private String status;
}
