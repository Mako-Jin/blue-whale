package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 制造看板聚合视图（内网只读，供 whale-scene 3D 看板取数）。
 *
 * <p>数量口径与 ERP 生产订单、库存余额完全一致：看板不做二次加工，
 * 对账脚本可直接逐项比对，避免「看板数字」与「库内数字」两套口径。</p>
 *
 * @author BlueWhale
 */
@Data
public class MfgBoardView {

    /** 数据生成时间 */
    private LocalDateTime generatedAt;

    /** 生产订单数量（按最近更新倒序截断） */
    private List<OrderLine> orders;

    /** 库存余额数量（按最近变动倒序截断） */
    private List<StockLine> stocks;

    /**
     * 生产订单行。
     */
    @Data
    public static class OrderLine {

        /** 生产订单号 */
        private String orderNo;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 计划数量 */
        private BigDecimal plannedQuantity;

        /** 完工数量 */
        private BigDecimal completedQuantity;

        /** 订单状态 */
        private String status;

        /** 完工入库仓库主键 */
        private Long targetWarehouseId;

        /** 完工入库仓库名称 */
        private String targetWarehouseName;

        /** MES 工单号（未下发为空） */
        private String mesWorkOrderCode;
    }

    /**
     * 库存余额行。
     */
    @Data
    public static class StockLine {

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 仓库主键 */
        private Long warehouseId;

        /** 现货数量 */
        private BigDecimal quantity;
    }
}
