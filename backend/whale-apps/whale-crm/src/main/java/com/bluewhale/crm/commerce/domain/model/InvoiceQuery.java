package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开票申请分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InvoiceQuery extends PageQuery {

    /** 关键字（申请单号 / 发票号码 / 客户名称） */
    private String keyword;

    /** 客户 ID */
    private Long customerId;

    /** 合同台账 ID */
    private Long contractId;

    /** 状态 */
    private String status;

    /** 申请类型 */
    private String requestType;

    /** 负责人（数据范围收口） */
    private Long ownerId;
}
