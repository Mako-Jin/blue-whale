package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.aftersale.application.command.AfterSaleApplyCommand;
import com.bluewhale.mall.aftersale.application.command.AfterSaleExchangeShipCommand;
import com.bluewhale.mall.aftersale.application.command.AfterSaleReturnLogisticsCommand;
import com.bluewhale.mall.aftersale.application.service.AfterSaleAppService;
import com.bluewhale.mall.aftersale.application.view.AfterSaleView;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
import com.bluewhale.mall.shipment.application.view.ShipmentView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后接口。
 *
 * <p>买家侧（申请、我的售后、详情、撤销、回填退货物流）与运营侧（全量列表、审核、
 * 确认收货、换货补发）分成两组路径：合成一组会让「能申请售后」和「能审核别人的售后」
 * 共用一个权限点。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/after-sale", version = "1")
@RequiredArgsConstructor
public class MallAfterSaleController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final AfterSaleAppService afterSaleAppService;

    /**
     * 申请售后。
     *
     * @param command 申请命令
     * @param request 当前请求
     * @return 售后单 ID
     */
    @PostMapping
    @RequirePermission(MallPermissions.AFTER_SALE_APPLY)
    public Result<Long> apply(@Valid @RequestBody AfterSaleApplyCommand command, HttpServletRequest request) {
        return Result.ok(afterSaleAppService.apply(command, MallActorContext.from(request)));
    }

    /**
     * 我的售后列表。
     *
     * @param status   状态过滤
     * @param keyword  售后单号/订单号关键字
     * @param current  后端风格页码
     * @param pageNum  前端风格页码
     * @param size     后端风格条数
     * @param pageSize 前端风格条数
     * @param request  当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.AFTER_SALE_APPLY)
    public Result<PageResult<AfterSaleView>> pageMine(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(afterSaleAppService.page(status, keyword,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                false, MallActorContext.from(request)));
    }

    /**
     * 售后列表（运营，全量）。
     *
     * @param status   状态过滤
     * @param keyword  售后单号/订单号关键字
     * @param current  后端风格页码
     * @param pageNum  前端风格页码
     * @param size     后端风格条数
     * @param pageSize 前端风格条数
     * @param request  当前请求
     * @return 分页结果
     */
    @GetMapping("/admin/page")
    @RequirePermission(MallPermissions.AFTER_SALE_LIST)
    public Result<PageResult<AfterSaleView>> pageAll(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(afterSaleAppService.page(status, keyword,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                true, MallActorContext.from(request)));
    }

    /**
     * 售后详情。
     *
     * @param id      售后单 ID
     * @param request 当前请求
     * @return 售后单视图
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.AFTER_SALE_APPLY)
    public Result<AfterSaleView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(afterSaleAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 买家撤销（仅待审核；换货单在待退货阶段也可撤销）。
     *
     * @param id      售后单 ID
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(MallPermissions.AFTER_SALE_APPLY)
    public Result<Void> cancel(@PathVariable("id") Long id, HttpServletRequest request) {
        afterSaleAppService.cancel(id, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 买家回填退货物流单号（待退货状态）。
     *
     * @param id      售后单 ID
     * @param command 回填命令
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/return-logistics")
    @RequirePermission(MallPermissions.AFTER_SALE_APPLY)
    public Result<Void> fillReturnLogistics(@PathVariable("id") Long id,
                                            @Valid @RequestBody AfterSaleReturnLogisticsCommand command,
                                            HttpServletRequest request) {
        afterSaleAppService.fillReturnLogistics(id, command, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 审核通过（仅退款立即退款；退货类进入待退货）。
     *
     * @param id      售后单 ID
     * @param remark  审核意见
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/approve")
    @RequirePermission(MallPermissions.AFTER_SALE_AUDIT)
    public Result<Void> approve(@PathVariable("id") Long id,
                                @RequestParam(value = "remark", required = false) String remark,
                                HttpServletRequest request) {
        afterSaleAppService.approve(id, remark, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 驳回售后（待审核与待退货均可驳）。
     *
     * @param id      售后单 ID
     * @param remark  驳回原因
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/reject")
    @RequirePermission(MallPermissions.AFTER_SALE_AUDIT)
    public Result<Void> reject(@PathVariable("id") Long id,
                               @RequestParam(value = "remark", required = false) String remark,
                               HttpServletRequest request) {
        afterSaleAppService.reject(id, remark, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 运营确认收到退货（待退货 → 审核通过并发起退款 / 换货补发中）。
     *
     * @param id      售后单 ID
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/confirm-return")
    @RequirePermission(MallPermissions.AFTER_SALE_AUDIT)
    public Result<Void> confirmReturn(@PathVariable("id") Long id, HttpServletRequest request) {
        afterSaleAppService.confirmReturn(id, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 换货补发：创建补发发货单并把售后单结到已换货。
     *
     * <p>补发明细固定为售后单的换货行，命令里只传承运商与运单号。
     * 权限沿用发货管理：补发实质上是一张发货单。</p>
     *
     * @param id      售后单 ID
     * @param command 补发命令
     * @param request 当前请求
     * @return 补发发货单视图
     */
    @PostMapping("/{id}/exchange-shipment")
    @RequirePermission(MallPermissions.SHIPMENT_MANAGE)
    public Result<ShipmentView> createExchangeShipment(@PathVariable("id") Long id,
                                                       @Valid @RequestBody AfterSaleExchangeShipCommand command,
                                                       HttpServletRequest request) {
        return Result.ok(afterSaleAppService.createExchangeShipment(id, command, MallActorContext.from(request)));
    }
}
