package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.StockBalance;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存余额视图。
 *
 * @author BlueWhale
 */
@Data
public class StockBalanceView {

    /** 主键 */
    private Long id;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库主键 */
    private Long warehouseId;

    /** 数量 */
    private BigDecimal quantity;

    /** 金额 */
    private BigDecimal amount;

    /**
     * 由领域对象构建视图。
     *
     * @param balance 余额
     * @return 视图
     */
    public static StockBalanceView of(StockBalance balance) {
        StockBalanceView view = new StockBalanceView();
        view.setId(balance.getId());
        view.setItemId(balance.getItemId());
        view.setItemCode(balance.getItemCode());
        view.setItemName(balance.getItemName());
        view.setWarehouseId(balance.getWarehouseId());
        view.setQuantity(balance.getQuantity());
        view.setAmount(balance.getAmount());
        return view;
    }
}
