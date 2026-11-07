package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑商务订单命令（仅草稿可编辑）。
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderUpdateCommand {

    /** 订单标题 */
    private String title;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 订单行（传入则全量替换） */
    @Valid
    private List<Line> lines = new ArrayList<>();

    /**
     * 订单行命令。
     *
     * @author BlueWhale
     */
    @Data
    public static class Line {

        /** ERP 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 规格 */
        private String specification;

        /** 计量单位 */
        private String uom;

        /** 数量 */
        @NotNull
        private BigDecimal quantity;

        /** 单价 */
        @NotNull
        private BigDecimal unitPrice;

        /** 行备注 */
        private String remark;
    }
}
