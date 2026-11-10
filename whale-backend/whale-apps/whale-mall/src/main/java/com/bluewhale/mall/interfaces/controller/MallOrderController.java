package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
import com.bluewhale.mall.order.application.command.OrderCreateCommand;
import com.bluewhale.mall.order.application.command.OrderPreviewCommand;
import com.bluewhale.mall.order.application.service.OrderAppService;
import com.bluewhale.mall.order.application.view.OrderPreviewView;
import com.bluewhale.mall.order.application.view.OrderView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 *
 * <p>下单要求请求头带 {@code Idempotency-Key}：用户双击、网络重试、
 * 前端自动重发都会产生重复提交，靠客户端「点了就禁用按钮」挡不住。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mall/order")
@RequiredArgsConstructor
public class MallOrderController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final OrderAppService orderAppService;

    /**
     * 下单试算。
     *
     * @param command 命令
     * @param request 当前请求
     * @return 试算结果
     */
    @PostMapping("/preview")
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<OrderPreviewView> preview(@RequestBody OrderPreviewCommand command,
                                            HttpServletRequest request) {
        return Result.ok(orderAppService.preview(command, MallActorContext.from(request)));
    }

    /**
     * 下单。
     *
     * @param idempotencyKey 幂等键（请求头）
     * @param command 命令
     * @param request 当前请求
     * @return 订单视图
     */
    @PostMapping
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<OrderView> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderCreateCommand command,
            HttpServletRequest request) {
        return Result.ok(orderAppService.create(command, idempotencyKey, MallActorContext.from(request)));
    }

    /**
     * 我的订单。
     *
     * @param status 状态过滤
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/my")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<PageResult<OrderView>> mine(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(orderAppService.pageMine(status,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 运营订单列表（全量）。
     *
     * @param status 状态过滤
     * @param keyword 订单号关键字
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<PageResult<OrderView>> page(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        return Result.ok(orderAppService.pageAll(status, keyword,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT),
                MallActorContext.from(request)));
    }

    /**
     * 订单详情。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 订单视图
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<OrderView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(orderAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 取消订单。
     *
     * @param id 订单 ID
     * @param reason 取消原因
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(MallPermissions.ORDER_CANCEL)
    public Result<Void> cancel(@PathVariable("id") Long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               HttpServletRequest request) {
        orderAppService.cancel(id, reason, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 买家发起支付（创建支付单并支付；成功后由 whale-pay 异步回调推进订单状态）。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 支付单号
     */
    @PostMapping("/{id}/pay")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<String> pay(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(orderAppService.pay(id, MallActorContext.from(request)));
    }

    /**
     * 整单退款（商城运营）：订单进入 REFUNDING，退款成功由 whale-pay 回调终态。
     *
     * @param id 订单 ID
     * @param reason 退款原因
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/refund")
    @RequirePermission(MallPermissions.ORDER_REFUND)
    public Result<Void> refund(@PathVariable("id") Long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               HttpServletRequest request) {
        orderAppService.refund(id, reason, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 完成订单。
     *
     * @param id 订单 ID
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/complete")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<Void> complete(@PathVariable("id") Long id, HttpServletRequest request) {
        orderAppService.complete(id, MallActorContext.from(request));
        return Result.ok();
    }
}
