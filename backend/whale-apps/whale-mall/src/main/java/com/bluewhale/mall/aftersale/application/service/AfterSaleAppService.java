package com.bluewhale.mall.aftersale.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.aftersale.application.command.AfterSaleApplyCommand;
import com.bluewhale.mall.aftersale.application.command.AfterSaleExchangeShipCommand;
import com.bluewhale.mall.aftersale.application.command.AfterSaleReturnLogisticsCommand;
import com.bluewhale.mall.aftersale.application.view.AfterSaleView;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.shipment.application.view.ShipmentView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 售后应用服务。
 *
 * <p>售后是整个商城唯一会把钱退回去的入口：整单退款也走这里（{@link #applyFullRefund}），
 * 不保留并行的第二条退款路径。两条路径意味着两套行额度计算、两套幂等处理、
 * 两处回调结单逻辑，迟早会有一处漏改。</p>
 *
 * @author BlueWhale
 */
public interface AfterSaleAppService {

    /**
     * 买家申请售后。
     *
     * @param command 申请命令
     * @param actor   操作人
     * @return 售后单 ID
     */
    Long apply(AfterSaleApplyCommand command, MallActor actor);

    /**
     * 审核通过。
     *
     * <p>按类型分流：仅退款审核即发起退款；退货类（退货退款 / 换货）进入待退货，
     * 等买家寄回商品、确认收货后再走退款或补发。</p>
     *
     * @param id     售后单 ID
     * @param remark 审核意见
     * @param actor  操作人
     */
    void approve(Long id, String remark, MallActor actor);

    /**
     * 驳回售后。
     *
     * <p>允许对待审核与待退货的售后单驳回，驳回即释放退款额度。</p>
     *
     * @param id     售后单 ID
     * @param remark 驳回原因
     * @param actor  操作人
     */
    void reject(Long id, String remark, MallActor actor);

    /**
     * 买家撤销售后。
     *
     * <p>仅待审核可撤；换货单在待退货阶段也可撤（不占退款额度，撤了不留悬账）。</p>
     *
     * @param id    售后单 ID
     * @param actor 操作人
     */
    void cancel(Long id, MallActor actor);

    /**
     * 买家回填退货物流单号（待退货状态可调）。
     *
     * @param id      售后单 ID
     * @param command 回填命令
     * @param actor   操作人
     */
    void fillReturnLogistics(Long id, AfterSaleReturnLogisticsCommand command, MallActor actor);

    /**
     * 运营确认收到退货。
     *
     * <p>退货退款回到审核通过状态并立即发起退款；换货进入换货补发中。</p>
     *
     * @param id    售后单 ID
     * @param actor 操作人
     */
    void confirmReturn(Long id, MallActor actor);

    /**
     * 换货补发：创建补发发货单并把售后单结到已换货。
     *
     * <p>复用发货链路占用行可发数量（条件更新），发货与终态同事务。</p>
     *
     * @param id      售后单 ID
     * @param command 补发命令（承运商 / 运单号）
     * @param actor   操作人
     * @return 补发发货单视图
     */
    ShipmentView createExchangeShipment(Long id, AfterSaleExchangeShipCommand command, MallActor actor);

    /**
     * 扫描超时未审核的售后单列表。
     *
     * <p>只扫 CREATED；逐单关闭由 {@link #closeExpired(Long)} 独立事务处理，
     * 单笔失败不影响其余。</p>
     *
     * @param expireHours 超时时长（小时）
     * @param limit       本次最多处理条数
     * @return 超时售后单列表
     */
    List<MallAfterSale> listExpiredCreated(int expireHours, int limit);

    /**
     * 关闭一张超时未审核的售后单（独立事务：转已撤销、释放额度、补系统流水）。
     *
     * @param id 售后单 ID
     */
    void closeExpired(Long id);

    /**
     * 分页查询售后单。
     *
     * @param status    状态过滤
     * @param keyword   售后单号/订单号关键字
     * @param current   页码
     * @param size      每页条数
     * @param manageAll 是否查看全量（运营）；false 时只看本人
     * @param actor     操作人
     * @return 分页结果
     */
    PageResult<AfterSaleView> page(String status, String keyword, Long current, Long size,
                                   boolean manageAll, MallActor actor);

    /**
     * 售后单详情。
     *
     * @param id    售后单 ID
     * @param actor 操作人
     * @return 售后单视图
     */
    AfterSaleView detail(Long id, MallActor actor);

    /**
     * 整单退款（管理端一键退）：生成覆盖全部可退行的售后单并直接通过审核。
     *
     * @param order  订单
     * @param reason 退款原因
     * @param actor  操作人
     * @return 售后单号（同时是支付侧退款幂等键）
     */
    String applyFullRefund(MallOrder order, String reason, MallActor actor);

    /**
     * 处理支付侧退款成功回调。
     *
     * <p>{@code requestNo} 是发起退款时传给支付侧的业务退款请求号，也就是售后单号；
     * 支付侧把它原样放在通知载荷的 {@code refundNo} 字段回传，据此定位要结哪张售后单。
     * 查不到时按「M1 历史退款单」兼容处理：只累计订单退款金额，不抛错——抛错会让
     * 投递方重试到死信队列，而在途的老单子恰恰是上线瞬间最容易出现的。</p>
     *
     * @param orderNo       订单号
     * @param requestNo     业务退款请求号（即售后单号）
     * @param paidBackTotal 支付侧累计退款金额（兼容分支用于推算本次退款额）
     */
    void markRefunded(String orderNo, String requestNo, BigDecimal paidBackTotal);
}
