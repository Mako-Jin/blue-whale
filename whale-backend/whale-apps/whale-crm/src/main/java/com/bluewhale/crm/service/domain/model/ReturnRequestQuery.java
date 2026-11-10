package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 退换货申请查询条件。
 *
 * @author BlueWhale
 */
@Data
public class ReturnRequestQuery {

    /** 当前页 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer size = 10;

    /** 关键字（申请单号 / 客户名称 / ERP 订单号） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 客户 ID */
    private Long customerId;

    /** 商务订单 ID */
    private Long orderId;

    /** 负责人（数据范围） */
    private Long ownerId;
}
