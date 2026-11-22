package com.bluewhale.erp.scm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.scm.application.command.PriceListCreateCommand;
import com.bluewhale.erp.scm.application.command.PriceListUpdateCommand;
import com.bluewhale.erp.scm.application.service.PriceCompareAppService;
import com.bluewhale.erp.scm.application.service.PriceListAppService;
import com.bluewhale.erp.scm.application.view.PriceListView;
import com.bluewhale.erp.scm.application.view.PriceTrendView;
import com.bluewhale.erp.scm.application.view.SupplierPriceCompareView;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 价目表接口：分页、详情、新建、更新、启停与取价。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/erp/v{version}/scm/price-list", version = "1")
@RequiredArgsConstructor
public class PriceListController {

    private final PriceListAppService priceListAppService;

    private final PriceCompareAppService priceCompareAppService;

    /**
     * 价目表分页。
     *
     * @param query 查询条件
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:price-list:list")
    public Result<PageResult<PriceListView>> page(
            @ModelAttribute PriceListQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(priceListAppService.page(query));
    }

    /**
     * 按日期取价。
     *
     * @param priceType 价格类型
     * @param partnerId 往来方主键
     * @param itemId 物料主键
     * @param date 业务日期（空取当天）
     * @return 命中的价目
     */
    @GetMapping("/resolve")
    @RequirePermission("sys:erp:price-list:resolve")
    public Result<PriceListView> resolve(
            @RequestParam PriceType priceType,
            @RequestParam Long partnerId,
            @RequestParam Long itemId,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(priceListAppService.resolve(priceType, partnerId, itemId, date));
    }

    /**
     * 价目详情。
     *
     * @param id 主键
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:erp:price-list:detail")
    public Result<PriceListView> detail(@PathVariable Long id) {
        return Result.ok(priceListAppService.detail(id));
    }

    /**
     * 新增价目。
     *
     * @param command 创建命令
     * @return 视图
     */
    @PostMapping
    @RequirePermission("sys:erp:price-list:create")
    @AuditLog(operation = "新增价目", targetType = "erp_scm_price_list", type = OperateType.CREATE)
    public Result<PriceListView> create(@Valid @RequestBody PriceListCreateCommand command) {
        return Result.ok(priceListAppService.create(command));
    }

    /**
     * 更新价目。
     *
     * @param id 主键
     * @param command 更新命令
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:erp:price-list:update")
    @AuditLog(operation = "更新价目", targetType = "erp_scm_price_list", type = OperateType.UPDATE)
    public Result<PriceListView> update(@PathVariable Long id,
                                        @Valid @RequestBody PriceListUpdateCommand command) {
        return Result.ok(priceListAppService.update(id, command));
    }

    /**
     * 切换价目启停状态。
     *
     * @param id 主键
     * @return 视图
     */
    @PostMapping("/{id}/toggle")
    @RequirePermission("sys:erp:price-list:toggle")
    @AuditLog(operation = "价目启停", targetType = "erp_scm_price_list", type = OperateType.UPDATE)
    public Result<PriceListView> toggle(@PathVariable Long id) {
        return Result.ok(priceListAppService.toggle(id));
    }

    /**
     * 供应商比价：当前有效采购价与最近成交价。
     *
     * @param itemId 物料主键
     * @param date 取价日期（空表示今天）
     * @return 比价列表
     */
    @GetMapping("/compare")
    @RequirePermission("sys:erp:price-compare:compare")
    public Result<List<SupplierPriceCompareView>> compare(
            @RequestParam Long itemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(priceCompareAppService.compare(itemId, date));
    }

    /**
     * 物料采购价格趋势。
     *
     * @param itemId 物料主键
     * @param months 统计月数（默认 6，范围 1–24）
     * @return 趋势列表
     */
    @GetMapping("/trend")
    @RequirePermission("sys:erp:price-compare:trend")
    public Result<List<PriceTrendView>> trend(
            @RequestParam Long itemId,
            @RequestParam(required = false) Integer months) {
        return Result.ok(priceCompareAppService.trend(itemId, months));
    }
}
