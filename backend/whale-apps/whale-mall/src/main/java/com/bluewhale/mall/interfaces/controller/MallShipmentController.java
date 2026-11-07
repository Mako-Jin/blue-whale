package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import com.bluewhale.mall.interfaces.support.PageParams;
import com.bluewhale.mall.shipment.application.command.ShipmentCancelCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentCreateCommand;
import com.bluewhale.mall.shipment.application.service.ShipmentAppService;
import com.bluewhale.mall.shipment.application.view.ShipmentView;
import com.bluewhale.mall.shipment.domain.model.ShipmentQuery;
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

import java.util.List;

/**
 * 商城发货单接口。
 *
 * <p>发货是履约的唯一入口：订单侧不再有独立的「发货」动作。一个订单可以拆成
 * 多个包裹（多张发货单），每张单对应一个运单号。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mall/shipment")
@RequiredArgsConstructor
public class MallShipmentController {

    /** 单页上限 */
    private static final int PAGE_LIMIT = 100;

    private final ShipmentAppService shipmentAppService;

    /**
     * 建发货单并发货。
     *
     * @param command 建单命令
     * @param request 当前请求
     * @return 发货单视图
     */
    @PostMapping
    @RequirePermission(MallPermissions.SHIPMENT_MANAGE)
    @AuditLog(operation = "建发货单并发货", type = OperateType.CREATE, targetType = "mall_shipment")
    public Result<ShipmentView> create(@Valid @RequestBody ShipmentCreateCommand command,
                                       HttpServletRequest request) {
        return Result.ok(shipmentAppService.create(command, MallActorContext.from(request)));
    }

    /**
     * 发货单分页。
     *
     * @param keyword     关键字（发货单号 / 订单号 / 运单号）
     * @param status      状态
     * @param carrierCode 承运商编码
     * @param orderId     订单 ID
     * @param current     后端风格页码
     * @param pageNum     前端风格页码
     * @param size        后端风格条数
     * @param pageSize    前端风格条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.SHIPMENT_LIST)
    public Result<PageResult<ShipmentView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "carrierCode", required = false) String carrierCode,
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        ShipmentQuery query = new ShipmentQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCarrierCode(carrierCode);
        query.setOrderId(orderId);
        return Result.ok(shipmentAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize, PAGE_LIMIT)));
    }

    /**
     * 某订单的发货单列表（买家可看自己的）。
     *
     * @param orderNo 订单号
     * @param request 当前请求
     * @return 发货单列表
     */
    @GetMapping("/order/{orderNo}")
    @RequirePermission(MallPermissions.ORDER_LIST)
    public Result<List<ShipmentView>> listByOrder(@PathVariable("orderNo") String orderNo,
                                                  HttpServletRequest request) {
        return Result.ok(shipmentAppService.listByOrderNo(orderNo, MallActorContext.from(request)));
    }

    /**
     * 发货单详情。
     *
     * @param id      主键
     * @param request 当前请求
     * @return 发货单视图
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.SHIPMENT_LIST)
    public Result<ShipmentView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(shipmentAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 手工签收（物流平台未接通时的兜底）。
     *
     * @param id      主键
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/delivered")
    @RequirePermission(MallPermissions.SHIPMENT_MANAGE)
    @AuditLog(operation = "发货单手工签收", type = OperateType.UPDATE, targetType = "mall_shipment")
    public Result<Void> delivered(@PathVariable("id") Long id, HttpServletRequest request) {
        shipmentAppService.deliver(id, MallActorContext.from(request));
        return Result.ok();
    }

    /**
     * 作废发货单（释放已发数量与预占库存）。
     *
     * @param id      主键
     * @param command 作废命令
     * @param request 当前请求
     * @return 空结果
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(MallPermissions.SHIPMENT_MANAGE)
    @AuditLog(operation = "作废发货单", type = OperateType.UPDATE, targetType = "mall_shipment")
    public Result<Void> cancel(@PathVariable("id") Long id,
                               @Valid @RequestBody(required = false) ShipmentCancelCommand command,
                               HttpServletRequest request) {
        shipmentAppService.cancel(id, command, MallActorContext.from(request));
        return Result.ok();
    }
}
