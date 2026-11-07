package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.converter.PayOrderConverter;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.entity.PayRefund;
import com.bluewhale.pay.entity.PayTransaction;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.enums.PayRefundStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.vo.PayRefundVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 退款单服务实现。
 *
 * <p>退款主流程：创建退款单（CREATED）→ 渠道退款 → 退款单 SUCCESS +
 * {@code pay_order} 原子累计退款额（全额自动 REFUNDED）→ 流水 → 业务通知。
 * 每一步失败抛业务异常回滚，不留中间态。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PayRefundServiceImpl implements PayRefundService {

    /** 退款单号前缀 */
    private static final String REFUND_NO_PREFIX = "RF";

    /** 流水类型：退款 */
    private static final String TYPE_REFUND = "REFUND";

    /** 流水状态：成功 */
    private static final String TX_SUCCESS = "SUCCESS";

    /** 金额最大小数位 */
    private static final int MAX_AMOUNT_SCALE = 2;

    /** 退款单号随机段长度 */
    private static final int RANDOM_SUFFIX_LEN = 4;

    private static final DateTimeFormatter REFUND_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PayOrderMapper payOrderMapper;

    private final PayRefundMapper payRefundMapper;

    private final PayTransactionMapper payTransactionMapper;

    private final PayChannelGateway payChannelGateway;

    private final PayNotifyService payNotifyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayRefundVO refundByNo(String payOrderNo, BigDecimal amount, String reason) {
        validateAmount(amount);
        PayOrder order = requireOrderByNo(payOrderNo);
        PayRefund refund = createRefundRecord(order, amount, reason);
        PayChannelResult result = payChannelGateway.refund(order, refund.getRefundNo(), amount);
        refund.setStatus(PayRefundStatus.SUCCESS.code());
        refund.setChannelRefundNo(result.providerTradeNo());
        refund.setRefundTime(LocalDateTime.now());
        payRefundMapper.updateById(refund);
        // 原子累计：0 行 = 状态不允许或累计超额，回滚整笔退款
        if (payOrderMapper.applyRefund(order.getId(), amount) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款失败：支付单不可退款或累计退款超出支付金额");
        }
        recordTransaction(order, amount, result);
        PayOrder fresh = payOrderMapper.selectById(order.getId());
        com.bluewhale.pay.entity.PayNotifyTask task =
                payNotifyService.enqueue(PayNotifyEvent.REFUNDED, fresh, refund.getRefundNo(),
                        fresh.getRefundAmount());
        payNotifyService.dispatchAfterCommit(task == null ? null : task.getId());
        return PayOrderConverter.INSTANCE.toRefundVo(refund);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayRefundVO refundFull(Long payOrderId, String reason) {
        PayOrder order = payOrderMapper.selectById(payOrderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        BigDecimal remaining = order.getAmount().subtract(
                order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付单已全额退款");
        }
        return refundByNo(order.getOrderNo(), remaining, reason);
    }

    @Override
    public PageResult<PayRefundVO> page(String keyword, long current, long size) {
        LambdaQueryWrapper<PayRefund> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(inner -> inner.like(PayRefund::getRefundNo, keyword)
                    .or().like(PayRefund::getPayOrderNo, keyword));
        }
        wrapper.orderByDesc(PayRefund::getCreateTime);
        long safeSize = Math.min(Math.max(size, 1L), 200L);
        Page<PayRefund> page = payRefundMapper.selectPage(new Page<>(current, safeSize), wrapper);
        List<PayRefundVO> records = page.getRecords().stream().map(PayOrderConverter.INSTANCE::toRefundVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PayRefundVO detail(Long id) {
        PayRefund refund = payRefundMapper.selectById(id);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return PayOrderConverter.INSTANCE.toRefundVo(refund);
    }

    /**
     * 创建退款单记录（CREATED 态）。
     *
     * @param order  支付单
     * @param amount 退款金额
     * @param reason 原因
     * @return 退款单
     */
    private PayRefund createRefundRecord(PayOrder order, BigDecimal amount, String reason) {
        PayRefund refund = new PayRefund();
        refund.setRefundNo(newRefundNo());
        refund.setPayOrderId(order.getId());
        refund.setPayOrderNo(order.getOrderNo());
        refund.setAmount(amount);
        refund.setReason(trimReason(reason));
        refund.setStatus(PayRefundStatus.CREATED.code());
        payRefundMapper.insert(refund);
        return refund;
    }

    /**
     * 记录退款流水。
     *
     * @param order  支付单
     * @param amount 金额
     * @param result 渠道结果
     */
    private void recordTransaction(PayOrder order, BigDecimal amount, PayChannelResult result) {
        PayTransaction transaction = new PayTransaction();
        transaction.setOrderId(order.getId());
        transaction.setOrderNo(order.getOrderNo());
        transaction.setType(TYPE_REFUND);
        transaction.setChannel(order.getChannel());
        transaction.setAmount(amount);
        transaction.setCurrency(order.getCurrency());
        transaction.setProviderTradeNo(result.providerTradeNo());
        transaction.setStatus(TX_SUCCESS);
        payTransactionMapper.insert(transaction);
    }

    /**
     * 按支付单号查询，不存在抛 NOT_FOUND。
     *
     * @param payOrderNo 支付单号
     * @return 支付单
     */
    private PayOrder requireOrderByNo(String payOrderNo) {
        PayOrder order = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOrderNo, payOrderNo));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    /**
     * 生成退款单号：RF + 时间戳 + 随机段（唯一键兜底）。
     *
     * @return 退款单号
     */
    private static String newRefundNo() {
        StringBuilder random = new StringBuilder(RANDOM_SUFFIX_LEN);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < RANDOM_SUFFIX_LEN; i++) {
            random.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return REFUND_NO_PREFIX + LocalDateTime.now().format(REFUND_NO_TIME) + random;
    }

    /**
     * 校验退款金额：大于 0 且最多 2 位小数。
     *
     * @param amount 金额
     */
    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额最多保留 2 位小数");
        }
    }

    /**
     * 截断退款原因到列长。
     *
     * @param reason 原因
     * @return 截断后原因
     */
    private static String trimReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }
}
