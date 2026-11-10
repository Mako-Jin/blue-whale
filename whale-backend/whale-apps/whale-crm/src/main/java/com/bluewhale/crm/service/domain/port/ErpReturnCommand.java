package com.bluewhale.crm.service.domain.port;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 销售退货下发命令（内网契约）。
 *
 * @author BlueWhale
 */
@Data
public class ErpReturnCommand {

    /** CRM 退换货申请单号（幂等键） */
    private String crmReturnNo;

    /** ERP 销售订单号（来源订单） */
    private String erpOrderNo;

    /** 单据日期 */
    private LocalDate docDate;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<Line> lines = new ArrayList<>();

    /**
     * 退货分录。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 来源订单行号 */
        private Integer orderLineNo;

        /** 退货数量 */
        private BigDecimal quantity;
    }
}
