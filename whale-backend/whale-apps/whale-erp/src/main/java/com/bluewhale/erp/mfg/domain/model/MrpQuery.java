package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MRP 分页查询条件（运行记录与计划订单共用）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MrpQuery extends PageQuery {

    /** 关键字：运行单号 / 计划订单号 / 物料编码 / 物料名称 */
    private String keyword;

    /** 状态（运行记录状态或计划订单状态） */
    private String status;

    /** 运行记录 ID */
    private Long runId;

    /** 计划订单建议类型：PURCHASE / PRODUCTION */
    private String orderType;
}
