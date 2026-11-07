package com.bluewhale.mall.order.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.order.application.command.OrderCreateCommand;
import com.bluewhale.mall.order.application.command.OrderPaidCommand;
import com.bluewhale.mall.order.application.command.OrderPreviewCommand;
import com.bluewhale.mall.order.application.view.OrderPreviewView;
import com.bluewhale.mall.order.application.view.OrderView;

/**
 * 订单应用服务。
 *
 * @author BlueWhale
 */
public interface OrderAppService {

    /**
     * 下单试算（不落库、不占库存）。
     *
     * @param command 命令
     * @param actor 操作人
     * @return 试算结果
     */
    OrderPreviewView preview(OrderPreviewCommand command, MallActor actor);

    /**
     * 下单（幂等）。
     *
     * @param command 命令
     * @param idempotencyKey 幂等键
     * @param actor 操作人
     * @return 订单视图（重复提交返回首次创建的订单）
     */
    OrderView create(OrderCreateCommand command, String idempotencyKey, MallActor actor);

    /**
     * 我的订单分页。
     *
     * @param status 状态过滤
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<OrderView> pageMine(String status, Long current, Long size, MallActor actor);

    /**
     * 运营订单分页（全量）。
     *
     * @param status 状态过滤
     * @param keyword 订单号关键字
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<OrderView> pageAll(String status, String keyword, Long current, Long size, MallActor actor);

    /**
     * 订单详情。
     *
     * @param id 订单 ID
     * @param actor 操作人
     * @return 订单视图
     */
    OrderView detail(Long id, MallActor actor);

    /**
     * 取消订单（释放库存预占）。
     *
     * @param id 订单 ID
     * @param reason 取消原因
     * @param actor 操作人
     */
    void cancel(Long id, String reason, MallActor actor);

    /**
     * 支付结果回调（幂等：已支付直接返回成功）。
     *
     * @param command 回调命令
     */
    void markPaid(OrderPaidCommand command);

    /**
     * 发货（扣减预占）。
     *
     * @param id 订单 ID
     * @param actor 操作人
     */
    void ship(Long id, MallActor actor);

    /**
     * 完成订单。
     *
     * @param id 订单 ID
     * @param actor 操作人
     */
    void complete(Long id, MallActor actor);
}
