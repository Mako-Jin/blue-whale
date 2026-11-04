package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.StockMovement;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水视图。
 *
 * @author BlueWhale
 */
@Data
public class StockMovementView {

    /** 主键 */
    private Long id;

    /** 关联单据号 */
    private String docNo;

    /** 来源类型 */
    private String sourceType;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库主键 */
    private Long warehouseId;

    /** 方向 */
    private String direction;

    /** 数量 */
    private BigDecimal quantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 金额 */
    private BigDecimal amount;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param movement 流水
     * @return 视图
     */
    public static StockMovementView of(StockMovement movement) {
        StockMovementView view = new StockMovementView();
        view.setId(movement.getId());
        view.setDocNo(movement.getDocNo());
        view.setSourceType(movement.getSourceType());
        view.setItemId(movement.getItemId());
        view.setItemCode(movement.getItemCode());
        view.setItemName(movement.getItemName());
        view.setWarehouseId(movement.getWarehouseId());
        view.setDirection(movement.getDirection());
        view.setQuantity(movement.getQuantity());
        view.setUnitCost(movement.getUnitCost());
        view.setAmount(movement.getAmount());
        view.setOccurredAt(movement.getOccurredAt());
        view.setRemark(movement.getRemark());
        return view;
    }
}
