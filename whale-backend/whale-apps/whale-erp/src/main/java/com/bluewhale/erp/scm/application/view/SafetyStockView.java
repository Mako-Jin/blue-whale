package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.SafetyStock;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 安全库存视图。
 *
 * @author BlueWhale
 */
@Data
public class SafetyStockView {

    /** 主键 */
    private Long id;

    /** 仓库主键 */
    private Long warehouseId;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 安全库存量 */
    private BigDecimal safetyQty;

    /** 最高库存量 */
    private BigDecimal maxQty;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param safetyStock 安全库存
     * @return 视图
     */
    public static SafetyStockView of(SafetyStock safetyStock) {
        SafetyStockView view = new SafetyStockView();
        view.setId(safetyStock.getId());
        view.setWarehouseId(safetyStock.getWarehouseId());
        view.setItemId(safetyStock.getItemId());
        view.setItemCode(safetyStock.getItemCode());
        view.setItemName(safetyStock.getItemName());
        view.setSafetyQty(safetyStock.getSafetyQty());
        view.setMaxQty(safetyStock.getMaxQty());
        view.setRemark(safetyStock.getRemark());
        return view;
    }
}
