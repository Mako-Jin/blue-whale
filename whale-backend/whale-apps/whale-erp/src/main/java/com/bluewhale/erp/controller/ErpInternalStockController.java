package com.bluewhale.erp.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.scm.application.service.InventoryAppService;
import com.bluewhale.erp.vo.ErpInternalStockView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 库存内网只读契约接口
 *
 * <p>供 whale-pdm 按物料编码回显各仓库库存。网关已将 {@code /erp/internal/**}
 * 加入 internal-deny，外部访问 403。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/stock")
@RequiredArgsConstructor
public class ErpInternalStockController {

    private final InventoryAppService inventoryAppService;

    /**
     * 按物料编码聚合库存
     *
     * @param itemCode 物料编码
     * @return 各仓库库存明细；无余额时数量为 0、明细为空
     */
    @GetMapping("/by-code")
    public Result<ErpInternalStockView> byCode(@RequestParam("itemCode") String itemCode) {
        return Result.ok(inventoryAppService.stockByItemCode(itemCode));
    }
}
