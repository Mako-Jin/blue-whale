package com.bluewhale.party.fee.domain.model;

import lombok.Data;

/**
 * 党费账单查询条件。
 *
 * @author BlueWhale
 */
@Data
public class FeeBillQuery {

    /** 所属月份 yyyyMM */
    private String periodMonth;

    /** 支部 ID */
    private Long branchId;

    /** 状态 */
    private String status;

    /** 关键字（党员姓名 / 编号） */
    private String keyword;

    /** 是否只看欠缴 */
    private Boolean outstanding;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
