package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.enums.PayRefundStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;
import com.bluewhale.pay.po.PayTransactionPo;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.service.PaySettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付结果落账实现。
 *
 * <p>状态推进一律使用条件更新抢占，多实例/并发回调下只有一个事务命中；
 * 未命中视为幂等重放，不重复写流水与通知。记账与通知入队在同一事务内，
 * 通知首投注册在提交之后，保证「账没记成功就不会通知业务方」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaySettlementServiceImpl implements PaySettlementService {

    /** 流水类型：支付 */
    private static final String TYPE_PAY = "PAY";

    /** 流水类型：退款 */
    private static final String TYPE_REFUND = "REFUND";

    /** 流水状态：成功 */
    private static final String TX_SUCCESS = "SUCCESS";

    /** 落账前允许的支付单状态：CREATED 供回调直达，PAYING 为同步抢占态 */
    private static final List<String> SETTLEABLE_STATUSES =
            List.of(PayOrderStatus.CREATED.code(), PayOrderStatus.PAYING.code());

    private final PayOrderMapper payOrderMapper;

    private final PayRefundMapper payRefundMapper;

    private final PayTransactionMapper payTransactionMapper;

    private final PayNotifyService payNotifyService;

    private final TransactionTemplate transactionTemplate;

    @Override
    public boolean markPaid(PayOrderPo order, PayChannelResult result) {
        if (result == null || !result.isSuccess()) {
            // 受理中不能落账，订单保持 PAYING 等回调或对账，避免把未确认的资金记为已支付
            log.warn("渠道结果非即时成功，支付单保持原状态: orderNo={} status={}",
                    order == null ? null : order.getOrderNo(),
                    result == null ? null : result.status());
            return false;
        }
        Boolean settled = transactionTemplate.execute(status -> settlePaid(order, result));
        return Boolean.TRUE.equals(settled);
    }

    /**
     * 落账事务：条件抢占 → 回填渠道流水 → 写支付流水 → 投递支付通知。
     *
     * @param order  支付单
     * @param result 渠道结果
     * @return 是否由本次事务完成落账
     */
    private boolean settlePaid(PayOrderPo order, PayChannelResult result) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PayOrderPo> update = new LambdaUpdateWrapper<>();
        update.eq(PayOrderPo::getId, order.getId())
                .in(PayOrderPo::getStatus, SETTLEABLE_STATUSES)
                .set(PayOrderPo::getStatus, PayOrderStatus.PAID.code())
                .set(PayOrderPo::getPayTime, now)
                .set(PayOrderPo::getUpdateTime, now);
        boolean hasTradeNo = StringUtils.hasText(result.providerTradeNo());
        if (hasTradeNo) {
            update.set(PayOrderPo::getChannelTradeNo, result.providerTradeNo());
        }
        if (payOrderMapper.update(null, update) == 0) {
            log.warn("支付单已被其他事务推进，跳过重复流水: id={}", order.getId());
            return false;
        }
        if (!hasTradeNo) {
            log.warn("渠道未返回流水号，落账后 channel_trade_no 为空，需人工补录: orderNo={}",
                    order.getOrderNo());
        }
        order.setStatus(PayOrderStatus.PAID.code());
        order.setPayTime(now);
        if (hasTradeNo) {
            order.setChannelTradeNo(result.providerTradeNo());
        }
        recordTransaction(order, TYPE_PAY, order.getAmount(), result);
        PayNotifyTaskPo task = payNotifyService.enqueue(PayNotifyEvent.PAID, order, null, null);
        payNotifyService.dispatchAfterCommit(task == null ? null : task.getId());
        return true;
    }

    @Override
    public void markRefunded(PayOrderPo order, PayRefundPo refund, BigDecimal amount, PayChannelResult result) {
        transactionTemplate.executeWithoutResult(status -> settleRefund(order, refund, amount, result));
    }

    /**
     * 退款记账事务：原子累计 → 退款单成功 → 写退款流水 → 投递退款通知。
     *
     * @param order  支付单
     * @param refund 退款单
     * @param amount 本次退款金额
     * @param result 渠道结果
     */
    private void settleRefund(PayOrderPo order, PayRefundPo refund, BigDecimal amount, PayChannelResult result) {
        // 原子累计：0 行 = 状态不允许或累计超额，整段记账回滚
        if (payOrderMapper.applyRefund(order.getId(), amount) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.not-refundable",
                    "退款失败：支付单不可退款或累计退款超出支付金额");
        }
        refund.setStatus(PayRefundStatus.SUCCESS.code());
        refund.setChannelRefundNo(result.providerTradeNo());
        refund.setRefundTime(LocalDateTime.now());
        payRefundMapper.updateById(refund);
        recordTransaction(order, TYPE_REFUND, amount, result);
        PayOrderPo fresh = payOrderMapper.selectById(order.getId());
        PayNotifyTaskPo task = payNotifyService.enqueue(PayNotifyEvent.REFUNDED, fresh,
                refund.getRefundNo(), fresh.getRefundAmount());
        payNotifyService.dispatchAfterCommit(task == null ? null : task.getId());
    }

    /**
     * 追加支付/退款成功流水。
     *
     * @param order  支付单
     * @param type   流水类型
     * @param amount 本次流水金额
     * @param result 渠道结果
     */
    private void recordTransaction(PayOrderPo order, String type, BigDecimal amount, PayChannelResult result) {
        PayTransactionPo transaction = new PayTransactionPo();
        transaction.setOrderId(order.getId());
        transaction.setOrderNo(order.getOrderNo());
        transaction.setType(type);
        transaction.setChannel(order.getChannel());
        transaction.setAmount(amount);
        transaction.setCurrency(order.getCurrency());
        transaction.setProviderTradeNo(result.providerTradeNo());
        transaction.setStatus(TX_SUCCESS);
        payTransactionMapper.insert(transaction);
    }
}
