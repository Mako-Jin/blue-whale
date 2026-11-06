package com.bluewhale.lms.credit.domain.model;

import lombok.Data;

/**
 * 学分台账查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CreditLedgerQuery {

    /** 关键字（学员用户名模糊） */
    private String keyword;

    /** 学员用户 ID */
    private Long userId;

    /** 来源类型 */
    private String sourceType;

    /** 归属年度 */
    private Integer periodYear;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
