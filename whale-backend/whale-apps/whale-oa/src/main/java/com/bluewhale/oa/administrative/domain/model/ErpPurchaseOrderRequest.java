package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 采购订单草稿请求。
 *
 * @author BlueWhale
 */
@Data
public class ErpPurchaseOrderRequest {

    /** 来源类型固定 OA_PURCHASE */
    private String sourceType;

    /** 来源采购申请ID */
    private Long sourceId;

    /** 来源单号 */
    private String sourceCode;

    /** 供应商ID */
    private Long supplierId;

    /** 收货仓库ID */
    private Long warehouseId;

    /** 单据日期 */
    private LocalDate docDate;

    /** 期望到货日期 */
    private LocalDate expectedDate;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 明细 */
    private List<Line> lines = new ArrayList<>();

    /**
     * 采购订单明细行。
     */
    @Data
    public static class Line {

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 数量 */
        private BigDecimal quantity;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 单位 */
        private String uom;
    }
}
