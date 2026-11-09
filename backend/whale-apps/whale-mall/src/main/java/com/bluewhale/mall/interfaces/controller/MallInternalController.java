package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.catalog.application.command.StockInboundCommand;
import com.bluewhale.mall.catalog.application.service.StockAppService;
import com.bluewhale.mall.catalog.application.view.StockInboundView;
import com.bluewhale.mall.order.application.command.OrderPaidCommand;
import com.bluewhale.mall.order.application.command.OrderRefundedCommand;
import com.bluewhale.mall.order.application.service.OrderAppService;
import com.bluewhale.mall.shipment.application.command.ShipmentDeliveredCommand;
import com.bluewhale.mall.shipment.application.service.ShipmentAppService;
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

    private final ShipmentAppService shipmentAppService;

    private final StockAppService stockAppService;

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

    /**
     * 退款结果回调（供 {@code whale-pay} 调用，幂等）。
     *
     * @param command 回调命令
     * @return 空结果
     */
    @PostMapping("/order/refunded")
    public Result<Void> orderRefunded(@Valid @RequestBody OrderRefundedCommand command) {
        orderAppService.markRefunded(command);
        return Result.ok();
    }

    /**
     * 签收回调（供物流平台/轨迹服务调用，幂等）。
     *
     * <p>按发货单号定位，重复推送直接返回成功、不重写签收时间：物流平台的重试
     * 会把「签收时间」刷成最后一次推送的时刻，与对账口径不符。</p>
     *
     * @param command 回调命令
     * @return 空结果
     */
    @PostMapping("/shipment/delivered")
    public Result<Void> shipmentDelivered(@Valid @RequestBody ShipmentDeliveredCommand command) {
        shipmentAppService.markDelivered(command);
        return Result.ok();
    }

    /**
     * WMS 入库联动（供 WMS/ERP 调用，docNo 幂等）。
     *
     * <p>把入库数量加回商城可售库存：重复推送同一单号直接返回首次入库结果
     * （duplicated = true），绝不二次加库存——这是库存类接口唯一能安全重试的方式。</p>
     *
     * @param command 入库命令
     * @return 入库结果
     */
    @PostMapping("/stock/inbound")
    public Result<StockInboundView> stockInbound(@Valid @RequestBody StockInboundCommand command) {
        return Result.ok(stockAppService.inbound(command));
    }
}
