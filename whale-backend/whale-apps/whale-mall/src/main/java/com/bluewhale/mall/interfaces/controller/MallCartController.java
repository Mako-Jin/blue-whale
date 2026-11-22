package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.cart.application.command.CartAddCommand;
import com.bluewhale.mall.cart.application.command.CartSelectCommand;
import com.bluewhale.mall.cart.application.service.CartAppService;
import com.bluewhale.mall.cart.application.view.CartView;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（买家自助）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/cart", version = "1")
@RequiredArgsConstructor
public class MallCartController {

    private final CartAppService cartAppService;

    /**
     * 查看购物车。
     *
     * @param channelId 渠道 ID
     * @param request 当前请求
     * @return 购物车视图
     */
    @GetMapping
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<CartView> view(@RequestParam("channelId") Long channelId, HttpServletRequest request) {
        return Result.ok(cartAppService.view(channelId, MallActorContext.from(request)));
    }

    /**
     * 加入购物车。
     *
     * @param command 命令
     * @param request 当前请求
     * @return 购物车视图
     */
    @PostMapping("/item")
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<CartView> add(@Valid @RequestBody CartAddCommand command, HttpServletRequest request) {
        return Result.ok(cartAppService.add(command, MallActorContext.from(request)));
    }

    /**
     * 修改数量。
     *
     * @param id 条目 ID
     * @param quantity 数量
     * @param request 当前请求
     * @return 购物车视图
     */
    @PutMapping("/item/{id}")
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<CartView> updateQuantity(@PathVariable("id") Long id,
                                           @RequestParam("quantity") Integer quantity,
                                           HttpServletRequest request) {
        return Result.ok(cartAppService.updateQuantity(id, quantity, MallActorContext.from(request)));
    }

    /**
     * 删除条目。
     *
     * @param id 条目 ID
     * @param request 当前请求
     * @return 购物车视图
     */
    @DeleteMapping("/item/{id}")
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<CartView> remove(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(cartAppService.remove(id, MallActorContext.from(request)));
    }

    /**
     * 批量修改选中态。
     *
     * @param command 命令
     * @param request 当前请求
     * @return 购物车视图
     */
    @PutMapping("/select")
    @RequirePermission(MallPermissions.CART_MANAGE)
    public Result<CartView> select(@RequestBody CartSelectCommand command, HttpServletRequest request) {
        return Result.ok(cartAppService.select(command, MallActorContext.from(request)));
    }
}
