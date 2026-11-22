package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.ChannelCallbackRequest;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.channel.PayChannelStatus;
import com.bluewhale.pay.enums.PayCallbackEvent;
import com.bluewhale.pay.enums.PayCallbackRejectReason;
import com.bluewhale.pay.enums.PayCallbackResult;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.enums.PayRefundStatus;
import com.bluewhale.pay.mapper.PayChannelCallbackLogMapper;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.po.PayChannelCallbackLogPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;
import com.bluewhale.pay.service.PayChannelCallbackService;
import com.bluewhale.pay.service.PaySettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 渠道异步回调入站实现。
 *
 * <p><b>幂等</b>：先插入 {@code pay_channel_callback_log}（幂等键唯一），插入成功者
 * 才有权处理；撞唯一键时读原记录，已终态（PROCESSED / REJECTED）直接按重发返回成功，
 * 仍是 FAILED（上次处理中断）则重新处理。</p>
 *
 * <p><b>并发</b>：领取到处理权后，在事务内对日志行 {@code SELECT ... FOR UPDATE}
 * 串行化同一幂等键的回调，避免并发重发导致重复退款；落账复用
 * {@link PaySettlementService}，自身不再写流水或通知。</p>
 *
 * <p><b>安全</b>：入站租户头一律忽略，租户由 {@code out_trade_no} 跨租户反查；
 * 金额或币种与本地不符只拒绝并留痕，禁止自动补偿。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayChannelCallbackServiceImpl implements PayChannelCallbackService {

    /** 原始报文最大留痕长度 */
    private static final int MAX_RAW_BODY = 4000;

    /** 无流水号时的幂等键占位段 */
    private static final String INVALID_KEY_SEGMENT = ":INVALID:";

    private final PayChannelGateway payChannelGateway;

    private final PayOrderMapper payOrderMapper;

    private final PayRefundMapper payRefundMapper;

    private final PayChannelCallbackLogMapper callbackLogMapper;

    private final PaySettlementService paySettlementService;

    private final TransactionTemplate transactionTemplate;

    @Override
    public String handle(String channel, String rawBody, Map<String, String> headers) {
        // 回调不信任入站租户头：任何伪造的 X-Tenant-Id 在此失效，租户只由支付单反查
        TenantContext.clear();
        String normalized = normalize(channel);
        try {
            ChannelCallbackRequest request = parse(normalized, rawBody, headers);
            if (request == null) {
                return response(normalized, false);
            }
            return processSafely(normalized, request, rawBody);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 验签并解析报文，失败落 {@code REJECTED/SIGN_INVALID}。
     *
     * @param channel 渠道编码
     * @param rawBody 原始报文
     * @param headers 请求头
     * @return 解析结果；验签失败返回 {@code null}
     */
    private ChannelCallbackRequest parse(String channel, String rawBody, Map<String, String> headers) {
        try {
            return payChannelGateway.parseCallback(channel, rawBody, headers);
        } catch (RuntimeException ex) {
            log.warn("渠道回调验签/解析失败: channel={}, reason={}", channel, ex.getMessage());
            recordSignatureFailure(channel, rawBody);
            return null;
        }
    }

    /**
     * 幂等占位后处理回调；未预期异常返回渠道失败体，等待渠道重发。
     *
     * @param channel 渠道编码
     * @param request 回调请求
     * @param rawBody 原始报文
     * @return 渠道响应体
     */
    private String processSafely(String channel, ChannelCallbackRequest request, String rawBody) {
        ClaimedLog claimed = claim(channel, request, rawBody);
        if (claimed == null) {
            return response(channel, false);
        }
        if (claimed.duplicate()) {
            return response(channel, true);
        }
        try {
            CallbackOutcome outcome = transactionTemplate.execute(
                    status -> processLocked(channel, request, claimed.row().getId()));
            return response(channel, outcome != null && outcome.result() == PayCallbackResult.PROCESSED);
        } catch (RuntimeException ex) {
            log.error("渠道回调处理异常告警: channel={}, outTradeNo={}", channel,
                    request.outTradeNo(), ex);
            return response(channel, false);
        }
    }

    /**
     * 幂等占位：插入日志（初始 FAILED），撞唯一键时读取原记录判断是否终态。
     *
     * @param channel 渠道编码
     * @param request 回调请求
     * @param rawBody 原始报文
     * @return 占位结果；无法确认原记录时返回 {@code null}
     */
    private ClaimedLog claim(String channel, ChannelCallbackRequest request, String rawBody) {
        try {
            PayChannelCallbackLogPo created = insertLog(null, channel, request,
                    PayCallbackResult.FAILED, null, rawBody);
            return new ClaimedLog(created, false);
        } catch (DuplicateKeyException ex) {
            PayChannelCallbackLogPo existing = findByKey(idempotencyKey(channel, request));
            if (existing == null) {
                log.warn("回调幂等键冲突但未读取到原记录: channel={}, key={}", channel,
                        idempotencyKey(channel, request));
                return null;
            }
            boolean terminal = PayCallbackResult.PROCESSED.code().equals(existing.getResult())
                    || PayCallbackResult.REJECTED.code().equals(existing.getResult());
            return new ClaimedLog(existing, terminal);
        } catch (RuntimeException ex) {
            log.error("回调日志落库失败: channel={}", channel, ex);
            return null;
        }
    }

    /**
     * 加行锁处理：再次确认终态后推进状态并回写日志，保证同一幂等键串行。
     *
     * @param channel 渠道编码
     * @param request 回调请求
     * @param logId   日志 ID
     * @return 处理结果
     */
    private CallbackOutcome processLocked(String channel, ChannelCallbackRequest request, Long logId) {
        PayChannelCallbackLogPo locked = callbackLogMapper.selectByIdForUpdate(logId);
        if (locked == null) {
            log.warn("回调日志不存在，跳过处理: channel={}, logId={}", channel, logId);
            return new CallbackOutcome(PayCallbackResult.FAILED, null, null);
        }
        if (PayCallbackResult.PROCESSED.code().equals(locked.getResult())
                || PayCallbackResult.REJECTED.code().equals(locked.getResult())) {
            return new CallbackOutcome(PayCallbackResult.DUPLICATE, null, locked.getTenantId());
        }
        CallbackOutcome outcome = process(request);
        locked.setTenantId(outcome.tenantId());
        locked.setResult(outcome.result().code());
        locked.setRejectReason(outcome.reason() == null ? null : outcome.reason().code());
        callbackLogMapper.updateById(locked);
        return outcome;
    }

    /**
     * 按事件推进本地状态：未知事件、单号不存在、金额不符一律拒绝且不改状态。
     *
     * @param request 回调请求
     * @return 处理结果
     */
    private CallbackOutcome process(ChannelCallbackRequest request) {
        PayCallbackEvent event = eventOf(request);
        if (event == PayCallbackEvent.UNKNOWN) {
            return reject(PayCallbackRejectReason.EVENT_UNSUPPORTED, null);
        }
        List<Long> tenantIds = payOrderMapper.selectTenantIdsByOrderNo(request.outTradeNo());
        if (tenantIds == null || tenantIds.size() != 1) {
            log.warn("渠道回调无法唯一定位支付单: outTradeNo={}, matched={}", request.outTradeNo(),
                    tenantIds == null ? 0 : tenantIds.size());
            return reject(PayCallbackRejectReason.ORDER_NOT_FOUND, null);
        }
        Long tenantId = tenantIds.get(0);
        TenantContext.set(tenantId);
        PayOrderPo order = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrderPo>()
                .eq(PayOrderPo::getOrderNo, request.outTradeNo()));
        if (order == null) {
            return reject(PayCallbackRejectReason.ORDER_NOT_FOUND, tenantId);
        }
        if (event == PayCallbackEvent.CLOSED) {
            return close(order, tenantId);
        }
        if (event == PayCallbackEvent.PAID) {
            return paid(order, request, tenantId);
        }
        return refunded(order, request, tenantId);
    }

    /**
     * 支付成功回调：复用统一落账，条件更新未命中且本地已支付视为幂等。
     *
     * @param order    支付单
     * @param request  回调请求
     * @param tenantId 租户
     * @return 处理结果
     */
    private CallbackOutcome paid(PayOrderPo order, ChannelCallbackRequest request, Long tenantId) {
        if (amountMismatch(request.amount(), order.getAmount(), request.currency(), order.getCurrency())) {
            return reject(PayCallbackRejectReason.AMOUNT_MISMATCH, tenantId);
        }
        boolean settled = paySettlementService.markPaid(order,
                new PayChannelResult(request.channelTradeNo(), PayChannelStatus.SUCCESS));
        if (settled) {
            return processed(tenantId);
        }
        PayOrderPo fresh = payOrderMapper.selectById(order.getId());
        if (fresh != null && isSettled(fresh.getStatus())) {
            return processed(tenantId);
        }
        return reject(PayCallbackRejectReason.STATUS_CONFLICT, tenantId);
    }

    /**
     * 退款成功回调：按退款单号定位并复用统一退款落账。
     *
     * @param order    支付单
     * @param request  回调请求
     * @param tenantId 租户
     * @return 处理结果
     */
    private CallbackOutcome refunded(PayOrderPo order, ChannelCallbackRequest request, Long tenantId) {
        if (!StringUtils.hasText(request.refundNo())) {
            return reject(PayCallbackRejectReason.REFUND_NOT_FOUND, tenantId);
        }
        PayRefundPo refund = payRefundMapper.selectOne(new LambdaQueryWrapper<PayRefundPo>()
                .eq(PayRefundPo::getRefundNo, request.refundNo()));
        if (refund == null || !order.getId().equals(refund.getPayOrderId())) {
            return reject(PayCallbackRejectReason.REFUND_NOT_FOUND, tenantId);
        }
        if (amountMismatch(request.amount(), refund.getAmount(), request.currency(), order.getCurrency())) {
            return reject(PayCallbackRejectReason.AMOUNT_MISMATCH, tenantId);
        }
        if (PayRefundStatus.SUCCESS.code().equals(refund.getStatus())) {
            return processed(tenantId);
        }
        // 先做状态与超额前置校验：避免在共享事务内靠异常回滚表达「不可退」，把可预期冲突变成脏日志
        if (!PayOrderStatus.PAID.code().equals(order.getStatus())
                || refundedAmount(order).add(refund.getAmount()).compareTo(order.getAmount()) > 0) {
            log.warn("退款回调与本地累计不一致: orderNo={}, refundNo={}, refunded={}",
                    order.getOrderNo(), refund.getRefundNo(), refundedAmount(order));
            return reject(PayCallbackRejectReason.STATUS_CONFLICT, tenantId);
        }
        paySettlementService.markRefunded(order, refund, refund.getAmount(),
                new PayChannelResult(request.channelTradeNo(), PayChannelStatus.SUCCESS));
        return processed(tenantId);
    }

    /**
     * 渠道关单回调：仅未支付单置为已取消，已支付视为状态冲突。
     *
     * @param order    支付单
     * @param tenantId 租户
     * @return 处理结果
     */
    private CallbackOutcome close(PayOrderPo order, Long tenantId) {
        if (payOrderMapper.closeIfUnpaid(order.getId()) > 0) {
            return processed(tenantId);
        }
        PayOrderPo fresh = payOrderMapper.selectById(order.getId());
        if (fresh != null && PayOrderStatus.CANCELLED.code().equals(fresh.getStatus())) {
            return processed(tenantId);
        }
        log.warn("渠道关单回调与本地状态冲突: orderNo={}, status={}", order.getOrderNo(),
                fresh == null ? null : fresh.getStatus());
        return reject(PayCallbackRejectReason.STATUS_CONFLICT, tenantId);
    }

    /**
     * 落回调日志（初始占位或验签失败）。
     *
     * @param tenantId 租户（可为空）
     * @param channel  渠道
     * @param request  回调请求
     * @param result   结果
     * @param reason   拒绝原因（可为空）
     * @param rawBody  原始报文
     * @return 已插入的日志行
     */
    private PayChannelCallbackLogPo insertLog(Long tenantId, String channel, ChannelCallbackRequest request,
                                              PayCallbackResult result, PayCallbackRejectReason reason,
                                              String rawBody) {
        PayChannelCallbackLogPo row = new PayChannelCallbackLogPo();
        row.setTenantId(tenantId);
        row.setChannel(channel);
        row.setChannelTradeNo(request.channelTradeNo());
        row.setOutTradeNo(request.outTradeNo());
        row.setEvent(eventOf(request).code());
        row.setIdempotencyKey(idempotencyKey(channel, request));
        row.setResult(result.code());
        row.setRejectReason(reason == null ? null : reason.code());
        row.setAmount(request.amount());
        row.setCurrency(request.currency());
        row.setRawBody(truncate(rawBody));
        callbackLogMapper.insert(row);
        return row;
    }

    /**
     * 验签失败留痕：无法取得流水号，幂等键追加随机段，保证每次攻击尝试可见。
     *
     * @param channel 渠道
     * @param rawBody 原始报文
     */
    private void recordSignatureFailure(String channel, String rawBody) {
        PayChannelCallbackLogPo row = new PayChannelCallbackLogPo();
        row.setChannel(channel);
        row.setEvent(PayCallbackEvent.UNKNOWN.code());
        row.setIdempotencyKey(channel + INVALID_KEY_SEGMENT + UUID.randomUUID());
        row.setResult(PayCallbackResult.REJECTED.code());
        row.setRejectReason(PayCallbackRejectReason.SIGN_INVALID.code());
        row.setRawBody(truncate(rawBody));
        try {
            callbackLogMapper.insert(row);
        } catch (RuntimeException ex) {
            log.error("回调验签失败日志落库失败: channel={}", channel, ex);
        }
    }

    /**
     * 按幂等键读取原日志。
     *
     * @param key 幂等键
     * @return 日志行；不存在返回 {@code null}
     */
    private PayChannelCallbackLogPo findByKey(String key) {
        return callbackLogMapper.selectOne(new LambdaQueryWrapper<PayChannelCallbackLogPo>()
                .eq(PayChannelCallbackLogPo::getIdempotencyKey, key));
    }

    /**
     * 幂等键：渠道 + 渠道流水号 + 事件；无流水号时用随机段区分每次尝试。
     *
     * @param channel 渠道
     * @param request 回调请求
     * @return 幂等键
     */
    private static String idempotencyKey(String channel, ChannelCallbackRequest request) {
        if (!StringUtils.hasText(request.channelTradeNo())) {
            return channel + INVALID_KEY_SEGMENT + UUID.randomUUID();
        }
        return channel + ":" + request.channelTradeNo() + ":" + eventOf(request).code();
    }

    /**
     * 渠道响应体，渠道不支持或解析异常时回落到默认值。
     *
     * @param channel 渠道
     * @param success 是否成功
     * @return 响应体
     */
    private String response(String channel, boolean success) {
        try {
            return success ? payChannelGateway.successResponse(channel)
                    : payChannelGateway.failureResponse(channel);
        } catch (RuntimeException ex) {
            log.warn("渠道响应体读取失败，使用默认值: channel={}, reason={}", channel, ex.getMessage());
            return success ? "SUCCESS" : "FAIL";
        }
    }

    /**
     * 归一化渠道编码。
     *
     * @param channel 渠道
     * @return 大写渠道编码
     */
    private static String normalize(String channel) {
        return channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 事件缺省视为未知。
     *
     * @param request 回调请求
     * @return 事件
     */
    private static PayCallbackEvent eventOf(ChannelCallbackRequest request) {
        return request.event() == null ? PayCallbackEvent.UNKNOWN : request.event();
    }

    /**
     * 金额或币种与本地不符。
     *
     * @param channelAmount   渠道金额
     * @param localAmount     本地金额
     * @param channelCurrency 渠道币种（空则不校验）
     * @param localCurrency   本地币种
     * @return 是否不符
     */
    private static boolean amountMismatch(BigDecimal channelAmount, BigDecimal localAmount,
                                          String channelCurrency, String localCurrency) {
        if (channelAmount == null || localAmount == null || channelAmount.compareTo(localAmount) != 0) {
            return true;
        }
        return StringUtils.hasText(channelCurrency) && !channelCurrency.equalsIgnoreCase(localCurrency);
    }

    /**
     * 已累计退款金额，空值按 0 处理。
     *
     * @param order 支付单
     * @return 已退款金额
     */
    private static BigDecimal refundedAmount(PayOrderPo order) {
        return order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
    }

    /**
     * 本地是否已处于已支付或已退款状态。
     *
     * @param status 状态码
     * @return 是否已落账
     */
    private static boolean isSettled(String status) {
        return PayOrderStatus.PAID.code().equals(status) || PayOrderStatus.REFUNDED.code().equals(status);
    }

    /**
     * 截断原始报文。
     *
     * @param rawBody 原始报文
     * @return 截断后报文
     */
    private static String truncate(String rawBody) {
        if (rawBody == null || rawBody.length() <= MAX_RAW_BODY) {
            return rawBody;
        }
        return rawBody.substring(0, MAX_RAW_BODY);
    }

    /**
     * 构造处理成功结果。
     *
     * @param tenantId 租户
     * @return 处理结果
     */
    private static CallbackOutcome processed(Long tenantId) {
        return new CallbackOutcome(PayCallbackResult.PROCESSED, null, tenantId);
    }

    /**
     * 构造拒绝结果。
     *
     * @param reason   拒绝原因
     * @param tenantId 租户（可为空）
     * @return 处理结果
     */
    private static CallbackOutcome reject(PayCallbackRejectReason reason, Long tenantId) {
        return new CallbackOutcome(PayCallbackResult.REJECTED, reason, tenantId);
    }

    /**
     * 幂等占位结果。
     *
     * @param row       日志行
     * @param duplicate 是否已存在终态记录（重发）
     */
    private record ClaimedLog(PayChannelCallbackLogPo row, boolean duplicate) {
    }

    /**
     * 回调处理结果。
     *
     * @param result   处理结果
     * @param reason   拒绝原因（非拒绝为 {@code null}）
     * @param tenantId 命中的租户（未命中为 {@code null}）
     */
    private record CallbackOutcome(PayCallbackResult result, PayCallbackRejectReason reason, Long tenantId) {
    }
}
