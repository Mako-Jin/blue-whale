package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 合同台账分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractQuery extends PageQuery {

    /** 关键字（合同编号 / 标题） */
    private String keyword;

    /** 客户 ID */
    private Long customerId;

    /** 合同状态 */
    private String status;

    /** 续约状态 */
    private String renewalStatus;

    /** 到期起始日（含） */
    private LocalDate expireFrom;

    /** 到期截止日（含） */
    private LocalDate expireTo;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
