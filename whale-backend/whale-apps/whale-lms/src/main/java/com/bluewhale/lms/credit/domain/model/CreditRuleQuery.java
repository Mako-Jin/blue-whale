package com.bluewhale.lms.credit.domain.model;

import lombok.Data;

/**
 * 学分规则查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CreditRuleQuery {

    /** 关键字（编码 / 名称模糊） */
    private String keyword;

    /** 来源类型 */
    private String sourceType;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
