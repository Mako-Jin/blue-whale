package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.order.application.command.OrderPaidCommand;
import com.bluewhale.mall.order.application.service.OrderAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城内网接口（仅服务间直连，网关侧对 {@code /mall/internal/**} 统一 403）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mall/internal")
@RequiredArgsConstructor
public class MallInternalController {

    private final OrderAppService orderAppService;

    /**
     * 支付结果回调（供 {@code whale-pay} 调用，幂等）。
     *
     * @param command 回调命令
     * @return 空结果
     */
    @PostMapping("/order/paid")
    public Result<Void> orderPaid(@Valid @RequestBody OrderPaidCommand command) {
        orderAppService.markPaid(command);
        return Result.ok();
    }
}
