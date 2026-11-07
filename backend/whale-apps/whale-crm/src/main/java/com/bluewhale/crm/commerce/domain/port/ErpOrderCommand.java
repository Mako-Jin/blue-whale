package com.bluewhale.crm.commerce.domain.port;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 销售订单下发命令（内网契约）。
 *
 * @author BlueWhale
 */
@Data
public class ErpOrderCommand {

    /** CRM 商务订单号（幂等键） */
    private String crmOrderNo;

    /** CRM 客户编码 */
    private String crmCustomerCode;

    /** 单据日期 */
    private LocalDate docDate;

    /** 计划发货日期 */
    private LocalDate deliveryDate;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<Line> lines = new ArrayList<>();

    /**
     * 下发分录。
     *
     * @author BlueWhale
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

        /** 计量单位 */
        private String uom;
    }
}
