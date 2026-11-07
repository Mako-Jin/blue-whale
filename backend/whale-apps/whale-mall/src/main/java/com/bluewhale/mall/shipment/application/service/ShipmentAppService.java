package com.bluewhale.mall.shipment.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.shipment.application.command.ShipmentCancelCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentCreateCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentDeliveredCommand;
import com.bluewhale.mall.shipment.application.view.ShipmentView;
import com.bluewhale.mall.shipment.domain.model.ShipmentQuery;

import java.util.List;

/**
 * 发货单应用服务。
 *
 * <p>发货单是履约的唯一入口：订单状态由它驱动（首次发货 → SHIPPED、
 * 全部发满且全部签收 → COMPLETED、全部作废 → 退回 PAID），
 * 订单侧不再有独立的「发货」动作。</p>
 *
 * @author BlueWhale
 */
public interface ShipmentAppService {

    /**
     * 建发货单并发货（支持拆包分批）。
     *
     * @param command 建单命令
     * @param actor 操作人
     * @return 发货单视图
     */
    ShipmentView create(ShipmentCreateCommand command, MallActor actor);

    /**
     * 发货单分页。
     *
     * @param query 查询条件
     * @param current 页码（从 1 开始）
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<ShipmentView> page(ShipmentQuery query, long current, long size);

    /**
     * 发货单详情（买家只能看自己订单的发货单）。
     *
     * @param id 主键
     * @param actor 操作人
     * @return 发货单视图
     */
    ShipmentView detail(Long id, MallActor actor);

    /**
     * 某订单的发货单列表（含已作废，买家可看自己的）。
     *
     * @param orderNo 订单号
     * @param actor 操作人
     * @return 发货单列表
     */
    List<ShipmentView> listByOrderNo(String orderNo, MallActor actor);

    /**
     * 运营手工签收（物流平台未接通时的兜底）。
     *
     * @param id 主键
     * @param actor 操作人
     */
    void deliver(Long id, MallActor actor);

    /**
     * 签收回调（内网，幂等）。
     *
     * @param command 回调命令
     */
    void markDelivered(ShipmentDeliveredCommand command);

    /**
     * 作废发货单（释放已发数量与预占库存）。
     *
     * @param id 主键
     * @param command 作废命令
     * @param actor 操作人
     */
    void cancel(Long id, ShipmentCancelCommand command, MallActor actor);
}
