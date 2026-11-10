package com.bluewhale.erp.scm.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.scm.application.command.StockAdjustCommand;
import com.bluewhale.erp.scm.application.view.StockBalanceView;
import com.bluewhale.erp.scm.application.view.StockMovementView;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.vo.ErpInternalStockView;

/**
 * 库存应用服务。
 *
 * @author BlueWhale
 */
public interface InventoryAppService {

    /**
     * 分页查询库存余额。
     *
     * @param query 查询条件
     * @return 余额分页
     */
    PageResult<StockBalanceView> balancePage(ScmQuery query);

    /**
     * 分页查询库存流水。
     *
     * @param query 查询条件
     * @return 流水分页
     */
    PageResult<StockMovementView> movementPage(ScmQuery query);

    /**
     * 库存调整（盘盈/盘亏）。
     *
     * @param command 调整命令
     * @param operatorId 操作人 ID
     * @return 生成的流水
     */
    StockMovementView adjust(StockAdjustCommand command, Long operatorId);

    /**
     * 按物料编码聚合各仓库库存（内网只读契约）
     *
     * @param itemCode 物料编码
     * @return 库存聚合视图
     */
    ErpInternalStockView stockByItemCode(String itemCode);
}
