package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 新建商务订单命令。
 *
 * <p>来源类型为 {@code QUOTE} 时可省略行，系统复制报价行（物料编码留空待补录）；
 * 来源类型为 {@code CONTRACT} 或 {@code MANUAL} 时必须提供行。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderCreateCommand {

    /** 来源类型：CONTRACT / QUOTE / MANUAL */
    private String sourceType = "MANUAL";

    /** 来源单据 ID（合同台账 ID 或报价 ID） */
    private Long sourceId;

    /** 客户 ID（来源单据可推导时可为空） */
    private Long customerId;

    /** 订单标题 */
    private String title;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 订单行 */
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
