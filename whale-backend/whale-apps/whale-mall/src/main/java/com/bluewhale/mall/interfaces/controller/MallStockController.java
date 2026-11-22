package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.catalog.application.command.StockAdjustCommand;
import com.bluewhale.mall.catalog.application.service.StockAppService;
import com.bluewhale.mall.catalog.application.view.StockView;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城库存接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/stock", version = "1")
@RequiredArgsConstructor
public class MallStockController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final StockAppService stockAppService;

    /**
     * 库存分页（按 SKU 编码筛选）。
     *
     * @param keyword SKU 编码关键字
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.STOCK_LIST)
    public Result<PageResult<StockView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(stockAppService.page(keyword,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 调整可售库存（增量）。
     *
     * @param skuId SKU ID
     * @param command 调整命令
     * @param request 当前请求
     * @return 调整后的可售数量
     */
    @PutMapping("/{skuId}")
    @RequirePermission(MallPermissions.STOCK_MANAGE)
    @AuditLog(operation = "调整商城库存", type = OperateType.UPDATE, targetType = "mall_sku_stock")
    public Result<Integer> adjust(@PathVariable("skuId") Long skuId,
                                  @Valid @RequestBody StockAdjustCommand command,
                                  HttpServletRequest request) {
        int delta = command.getDelta() == null ? 0 : command.getDelta();
        return Result.ok(stockAppService.adjust(skuId, delta, MallActorContext.from(request)));
    }
}
