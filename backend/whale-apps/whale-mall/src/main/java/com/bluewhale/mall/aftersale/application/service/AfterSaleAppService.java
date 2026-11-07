package com.bluewhale.mall.aftersale.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.aftersale.application.command.AfterSaleApplyCommand;
import com.bluewhale.mall.aftersale.application.view.AfterSaleView;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.order.domain.model.MallOrder;

import java.math.BigDecimal;

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
     * 审核通过并发起退款。
     *
     * @param id     售后单 ID
     * @param remark 审核意见
     * @param actor  操作人
     */
    void approve(Long id, String remark, MallActor actor);

    /**
     * 驳回售后。
     *
     * @param id     售后单 ID
     * @param remark 驳回原因
     * @param actor  操作人
     */
    void reject(Long id, String remark, MallActor actor);

    /**
     * 买家撤销售后（仅待审核）。
     *
     * @param id    售后单 ID
     * @param actor 操作人
     */
    void cancel(Long id, MallActor actor);

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
