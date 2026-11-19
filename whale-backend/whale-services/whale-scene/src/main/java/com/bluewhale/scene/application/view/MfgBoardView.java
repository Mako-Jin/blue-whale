package com.bluewhale.scene.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 3D 看板制造数据视图。
 *
 * <p>数量全部来自 ERP 生产订单与库存余额；ERP 不可达时 {@code degraded=true}，
 * 前端必须显式降级提示，不得展示任何假数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class MfgBoardView {

    /** 是否降级（数据源不可用或无租户上下文） */
    private boolean degraded;

    /** 降级原因（仅在 degraded=true 时有值） */
    private String message;

    /** 数据生成时间（ISO-8601 文本，避免跨服务时区解析差异） */
    private String generatedAt;

    /** 生产订单数量 */
    private List<Order> orders = new ArrayList<>();

    /** 库存余额数量 */
    private List<Stock> stocks = new ArrayList<>();

    /**
     * 生产订单行。
     */
    @Data
    public static class Order {

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

        /** MES 工单号 */
        private String mesWorkOrderCode;
    }

    /**
     * 库存余额行。
     */
    @Data
    public static class Stock {

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
