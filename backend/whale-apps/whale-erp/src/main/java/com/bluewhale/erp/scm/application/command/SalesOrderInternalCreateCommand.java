package com.bluewhale.erp.scm.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CRM 商务订单内网下发命令。
 *
 * <p>以 {@code crmOrderNo} 为幂等键：重复请求返回 ERP 已存在的原单，不重复建单。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderInternalCreateCommand {

    /** CRM 商务订单号（幂等键） */
    @NotBlank
    private String crmOrderNo;

    /** CRM 客户编码（ERP 客户主数据编码） */
    @NotBlank
    private String crmCustomerCode;

    /** 单据日期（为空按当天） */
    private LocalDate docDate;

    /** 计划发货日期 */
    private LocalDate deliveryDate;

    /** 发货仓库主键（为空取第一个启用仓库） */
    private Long warehouseId;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 分录 */
    @Valid
    @NotEmpty
    private List<Line> lines = new ArrayList<>();

    /**
     * 销售订单分录命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank
        private String itemCode;

        /** 数量 */
        @NotNull
        private BigDecimal quantity;

        /** 单价（为空时按客户售价兜底取价） */
        @Positive
        private BigDecimal unitPrice;

        /** 计量单位 */
        private String uom;
    }
}
