package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.converter.PayOrderConverter;
import com.bluewhale.pay.enums.PayChannelFeature;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;
import com.bluewhale.pay.enums.PayRefundStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.service.PaySettlementService;
import com.bluewhale.pay.vo.PayRefundVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 退款单服务实现。
 *
 * <p>退款主流程：创建退款单（CREATED）→ 渠道退款 → 退款单 SUCCESS +
 * {@code pay_order} 原子累计退款额（全额自动 REFUNDED）→ 流水 → 业务通知。</p>
 *
 * <p><b>事务边界（P0-2）：</b>拆成「建单提交 → 事务外调渠道 → 记账提交」三段，
 * 渠道网络往返不持有数据库事务。渠道明确失败或记账失败时，把退款单置为
 * {@code FAILED} 并保留原始异常供调用方重试；渠道成功但本地记账失败属于资金不一致，
 * {@code FAILED} 单即对账入口，由运营按渠道流水核实后处理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayRefundServiceImpl implements PayRefundService {

    /** 退款单号前缀 */
    private static final String REFUND_NO_PREFIX = "RF";

    /** 金额最大小数位 */
    private static final int MAX_AMOUNT_SCALE = 2;

    /** 退款单号随机段长度 */
    private static final int RANDOM_SUFFIX_LEN = 4;

    private static final DateTimeFormatter REFUND_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PayOrderMapper payOrderMapper;

    private final PayRefundMapper payRefundMapper;

    private final PayChannelGateway payChannelGateway;

    private final PaySettlementService paySettlementService;

    private final TransactionTemplate transactionTemplate;

    @Override
    public PayRefundVO refundByNo(String payOrderNo, String refundNo, BigDecimal amount, String reason) {
        validateAmount(amount);
        return doRefund(requireOrderByNo(payOrderNo), refundNo, amount, reason);
    }

    @Override
    public PayRefundVO refundFull(Long payOrderId, String reason) {
        PayOrderPo order = payOrderMapper.selectById(payOrderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        BigDecimal remaining = remainingRefundable(order);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.already-full-refunded", "支付单已全额退款");
        }
        return doRefund(order, null, remaining, reason);
    }

    @Override
    public PayRefundVO refundPartial(Long payOrderId, String clientRequestId, BigDecimal amount, String reason) {
        validateAmount(amount);
        PayOrderPo order = payOrderMapper.selectById(payOrderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        ensurePartialRefundable(order, amount);
        return doRefund(order, clientRequestId, amount, reason);
    }

    /**
     * 退款主流程：幂等判重并建退款单（提交）→ 事务外渠道退款 → 记账事务。
     *
     * @param order    支付单
     * @param refundNo 业务退款请求号（空则自动生成）
     * @param amount   退款金额
     * @param reason   退款原因
     * @return 退款单
     */
    private PayRefundVO doRefund(PayOrderPo order, String refundNo, BigDecimal amount, String reason) {
        ensureChannelSupportsAmount(order, amount);
        PreparedRefund prepared = prepareRefund(order, refundNo, amount, reason);
        if (!prepared.fresh()) {
            // 幂等回放（含并发同号请求）：已有退款单直接返回，不再发起渠道退款
            return PayOrderConverter.INSTANCE.toRefundVo(prepared.refund());
        }
        PayRefundPo refund = prepared.refund();
        PayChannelResult result;
        try {
            // 事务外调用渠道：退款网络往返不持有数据库事务
            result = payChannelGateway.refund(order, refund.getRefundNo(), amount);
        } catch (RuntimeException ex) {
            markRefundFailed(refund.getId());
            throw ex;
        }
        try {
            // 与支付成功共用同一落账入口：原子累计 → 退款单成功 → 流水 → 通知
            paySettlementService.markRefunded(order, refund, amount, result);
        } catch (RuntimeException ex) {
            // 渠道可能已退款成功，保留 FAILED 单供对账核实
            markRefundFailed(refund.getId());
            throw ex;
        }
        return PayOrderConverter.INSTANCE.toRefundVo(refund);
    }

    /**
     * 建单事务：幂等判重 + 插入 CREATED 退款单并立即提交。
     *
     * <p>并发同号请求撞 {@code uk_tenant_refund_no} 时回读已存在原单，按幂等回放处理。</p>
     *
     * @param order    支付单
     * @param refundNo 业务退款请求号
     * @param amount   退款金额
     * @param reason   退款原因
     * @return 退款单与是否本次新建
     */
    private PreparedRefund prepareRefund(PayOrderPo order, String refundNo,
                                         BigDecimal amount, String reason) {
        try {
            return transactionTemplate.execute(status -> {
                PayRefundPo replayed = findReplayedRefund(refundNo, amount);
                if (replayed != null) {
                    return new PreparedRefund(replayed, false);
                }
                return new PreparedRefund(createRefundRecord(order, refundNo, amount, reason), true);
            });
        } catch (DuplicateKeyException ex) {
            PayRefundPo concurrent = findReplayedRefund(refundNo, amount);
            if (concurrent != null) {
                return new PreparedRefund(concurrent, false);
            }
            throw ex;
        }
    }

    /**
     * 将退款单置为 FAILED（独立事务），作为失败/不一致的对账入口。
     *
     * <p>置败失败只记录日志，不覆盖调用方即将抛出的原始异常——真正重要的是
     * 让调用方看到渠道/记账失败原因，退款单状态可由对账补齐。</p>
     *
     * @param refundId 退款单ID
     */
    private void markRefundFailed(Long refundId) {
        if (refundId == null) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                PayRefundPo update = new PayRefundPo();
                update.setId(refundId);
                update.setStatus(PayRefundStatus.FAILED.code());
                payRefundMapper.updateById(update);
            });
        } catch (RuntimeException ex) {
            log.error("退款单置 FAILED 失败，需人工对账: refundId={}", refundId, ex);
        }
    }

    /**
     * 建单事务结果：退款单与是否本次新建。
     *
     * @param refund 退款单
     * @param fresh  是否本次新建（true 才需要调渠道）
     */
    private record PreparedRefund(PayRefundPo refund, boolean fresh) {
    }

    @Override
    public PageResult<PayRefundVO> page(String keyword, long current, long size) {
        LambdaQueryWrapper<PayRefundPo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(inner -> inner.like(PayRefundPo::getRefundNo, keyword)
                    .or().like(PayRefundPo::getPayOrderNo, keyword));
        }
        wrapper.orderByDesc(PayRefundPo::getCreateTime);
        long safeSize = Math.min(Math.max(size, 1L), 200L);
        Page<PayRefundPo> page = payRefundMapper.selectPage(new Page<>(current, safeSize), wrapper);
        List<PayRefundVO> records = withProgress(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PayRefundVO detail(Long id) {
        PayRefundPo refund = payRefundMapper.selectById(id);
        if (refund == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return withProgress(List.of(refund)).get(0);
    }

    /**
     * 剩余可退金额。
     *
     * @param order 支付单
     * @return amount - 累计退款额，最小为 0
     */
    private static BigDecimal remainingRefundable(PayOrderPo order) {
        BigDecimal refunded = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        BigDecimal remaining = order.getAmount().subtract(refunded);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    /**
     * 部分退款前置校验：必须已支付、金额不超过剩余可退。
     *
     * @param order  支付单
     * @param amount 本次退款金额
     */
    private static void ensurePartialRefundable(PayOrderPo order, BigDecimal amount) {
        if (!PayOrderStatus.PAID.code().equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.not-refundable", "退款失败：支付单不可退款");
        }
        BigDecimal remaining = remainingRefundable(order);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.already-full-refunded", "支付单已全额退款");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.amount-exceeds-refundable",
                    "退款金额超过剩余可退金额: " + remaining, remaining);
        }
    }

    /**
     * 渠道能力校验：部分退款要求渠道声明 {@code PARTIAL_REFUND}。
     *
     * @param order  支付单
     * @param amount 本次退款金额
     */
    private void ensureChannelSupportsAmount(PayOrderPo order, BigDecimal amount) {
        if (amount.compareTo(remainingRefundable(order)) < 0
                && !payChannelGateway.supports(order.getChannel(), PayChannelFeature.PARTIAL_REFUND)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.partial-not-supported",
                    "当前渠道不支持部分退款，请按剩余可退金额全额退款", order.getChannel());
        }
    }

    /**
     * 退款单转 VO 并补齐所属支付单的累计退款进度。
     *
     * @param refunds 退款单列表
     * @return 带进度信息的 VO 列表
     */
    private List<PayRefundVO> withProgress(List<PayRefundPo> refunds) {
        List<PayRefundVO> vos = refunds.stream().map(PayOrderConverter.INSTANCE::toRefundVo).toList();
        Set<String> orderNos = refunds.stream().map(PayRefundPo::getPayOrderNo)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        if (orderNos.isEmpty()) {
            return vos;
        }
        Map<String, PayOrderPo> orders = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrderPo>()
                        .in(PayOrderPo::getOrderNo, orderNos)).stream()
                .collect(Collectors.toMap(PayOrderPo::getOrderNo, Function.identity(), (first, second) -> first));
        for (PayRefundVO vo : vos) {
            PayOrderPo order = orders.get(vo.getPayOrderNo());
            if (order != null) {
                vo.setOrderAmount(order.getAmount());
                vo.setTotalRefundedAmount(order.getRefundAmount());
            }
        }
        return vos;
    }

    /**
     * 幂等查询：同一业务退款请求号是否已经退过。
     *
     * <p>命中且金额一致说明是调用方重试（网络超时、回调重放），返回原退款单即可；
     * 命中但金额不同说明请求号被挪用，直接拒绝——静默按原单返回会让调用方
     * 以为新金额也退了。</p>
     *
     * <p>并发下两个同号请求可能都查不到，此时后插的那个会撞 {@code uk_tenant_refund_no}
     * 抛重复键、整笔回滚，调用方重试即可拿到原单。「宁可失败不可多退」。</p>
     *
     * @param refundNo 业务退款请求号
     * @param amount   本次退款金额
     * @return 已存在的退款单，无则 null
     */
    private PayRefundPo findReplayedRefund(String refundNo, BigDecimal amount) {
        if (!StringUtils.hasText(refundNo)) {
            return null;
        }
        PayRefundPo existing = payRefundMapper.selectOne(new LambdaQueryWrapper<PayRefundPo>()
                .eq(PayRefundPo::getRefundNo, refundNo));
        if (existing == null) {
            return null;
        }
        if (existing.getAmount().compareTo(amount) != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.request-reused",
                    "退款请求号 " + refundNo + " 已用于金额 " + existing.getAmount() + " 的退款，不可改额重试",
                    refundNo, existing.getAmount());
        }
        return existing;
    }

    /**
     * 创建退款单记录（CREATED 态）。
     *
     * @param order    支付单
     * @param refundNo 业务退款请求号（空则自动生成）
     * @param amount   退款金额
     * @param reason   原因
     * @return 退款单
     */
    private PayRefundPo createRefundRecord(PayOrderPo order, String refundNo, BigDecimal amount, String reason) {
        PayRefundPo refund = new PayRefundPo();
        refund.setRefundNo(StringUtils.hasText(refundNo) ? refundNo.trim() : newRefundNo());
        refund.setPayOrderId(order.getId());
        refund.setPayOrderNo(order.getOrderNo());
        refund.setAmount(amount);
        refund.setReason(trimReason(reason));
        refund.setStatus(PayRefundStatus.CREATED.code());
        payRefundMapper.insert(refund);
        return refund;
    }

    /**
     * 按支付单号查询，不存在抛 NOT_FOUND。
     *
     * @param payOrderNo 支付单号
     * @return 支付单
     */
    private PayOrderPo requireOrderByNo(String payOrderNo) {
        PayOrderPo order = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrderPo>()
                .eq(PayOrderPo::getOrderNo, payOrderNo));
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
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.amount-invalid", "退款金额必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.refund.amount-scale-invalid", "退款金额最多保留 2 位小数");
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
