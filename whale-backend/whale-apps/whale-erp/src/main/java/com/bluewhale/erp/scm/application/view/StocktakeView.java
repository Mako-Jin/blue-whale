package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.Stocktake;
import com.bluewhale.erp.scm.domain.model.StocktakeLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存盘点单视图。
 *
 * @author BlueWhale
 */
@Data
public class StocktakeView {

    /** 主键 */
    private Long id;

    /** 盘点单号 */
    private String docNo;

    /** 盘点日期 */
    private LocalDate stocktakeDate;

    /** 盘点仓库 */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 状态 */
    private String status;

    /** 盘盈金额 */
    private BigDecimal gainAmount;

    /** 盘亏金额 */
    private BigDecimal lossAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<LineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param stocktake 盘点单
     * @return 视图
     */
    public static StocktakeView of(Stocktake stocktake) {
        StocktakeView view = new StocktakeView();
        view.setId(stocktake.getId());
        view.setDocNo(stocktake.getDocNo());
        view.setStocktakeDate(stocktake.getStocktakeDate());
        view.setWarehouseId(stocktake.getWarehouseId());
        view.setWarehouseName(stocktake.getWarehouseName());
        view.setStatus(stocktake.getStatus());
        view.setGainAmount(stocktake.getGainAmount());
        view.setLossAmount(stocktake.getLossAmount());
        view.setRemark(stocktake.getRemark());
        if (stocktake.getLines() != null) {
            view.setLines(stocktake.getLines().stream().map(LineView::of).toList());
        }
        return view;
    }

    /**
     * 盘点分录视图。
     */
    @Data
    public static class LineView {

        /** 行号 */
        private Integer lineNo;

        /** 物料主键 */
        private Long itemId;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 计量单位 */
        private String uom;

        /** 账面数量 */
        private BigDecimal bookQuantity;

        /** 实盘数量 */
        private BigDecimal countedQuantity;

        /** 差异数量 */
        private BigDecimal difference;

        /** 单位成本 */
        private BigDecimal unitCost;

        /** 差异金额 */
        private BigDecimal amount;

        /**
         * 由领域对象构建视图。
         *
         * @param line 分录
         * @return 视图
         */
        public static LineView of(StocktakeLine line) {
            LineView view = new LineView();
            view.setLineNo(line.getLineNo());
            view.setItemId(line.getItemId());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setUom(line.getUom());
            view.setBookQuantity(line.getBookQuantity());
            view.setCountedQuantity(line.getCountedQuantity());
            view.setDifference(line.getDifference());
            view.setUnitCost(line.getUnitCost());
            view.setAmount(line.getAmount());
            return view;
        }
    }
}
