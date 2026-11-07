package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.converter.PayOrderConverter;
import com.bluewhale.pay.dto.PayInternalCreateRequest;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.entity.PayTransaction;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.service.PayOrderService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayReconcileVO;
import com.bluewhale.pay.vo.PayTransactionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 支付单服务实现
 *
 * <p>创建前校验金额与渠道、按 {@code order_no} 查重（并发撞唯一索引时同样转为参数错误）；
 * 状态流转单向：{@code CREATED → PAID / CANCELLED}、{@code PAID → REFUNDED}。
 * 状态流转通过「条件更新抢占」（{@code update ... where status = 期望状态}）保证并发安全：
 * 同一支付单的并发支付/取消/退款只有一个事务能赢得抢占，其余直接失败，
 * 从而杜绝重复扣款、重复退款与重复流水；渠道调用失败时整个事务（含抢占）回滚。
 * 支付与退款成功后追加 {@code pay_transaction} 流水，并回填渠道流水号与退款信息；
 * 对账把本地状态与渠道状态比对后返回一致性结果。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements PayOrderService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;


    /** 流水类型：支付 */
    private static final String TYPE_PAY = "PAY";

    /** 流水类型：退款 */
    private static final String TYPE_REFUND = "REFUND";

    /** 流水状态：成功 */
    private static final String TX_SUCCESS = "SUCCESS";

    /** 默认渠道：mock */
    private static final String DEFAULT_CHANNEL = "MOCK";

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 金额最大小数位 */
    private static final int MAX_AMOUNT_SCALE = 2;

    private final PayOrderMapper payOrderMapper;

    private final PayTransactionMapper payTransactionMapper;

    private final PayChannelGateway payChannelGateway;

    private final PayNotifyService payNotifyService;

    private final PayRefundService payRefundService;

    @Override
    public PageResult<PayOrderVO> page(PayOrderQueryRequest request) {
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(inner -> inner.like(PayOrder::getOrderNo, request.getKeyword())
                    .or().like(PayOrder::getBusinessKey, request.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(request.getStatus()), PayOrder::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getChannel()), PayOrder::getChannel, request.getChannel());
        wrapper.orderByDesc(PayOrder::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PayOrder> page = payOrderMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PayOrderVO> records = page.getRecords().stream().map(PayOrderServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PayOrderVO getDetail(Long id) {
        return toVo(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO create(PayOrderCreateRequest request) {
        validateAmount(request.getAmount());
        String orderNo = request.getOrderNo().trim();
        ensureUnique(orderNo);
        PayOrder entity = PayOrderConverter.INSTANCE.toEntity(request);
        entity.setOrderNo(orderNo);
        entity.setCurrency(normalize(request.getCurrency(), DEFAULT_CURRENCY));
        entity.setChannel(normalize(request.getChannel(), DEFAULT_CHANNEL));
        if (!payChannelGateway.isSupported(entity.getChannel())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的支付渠道: " + entity.getChannel());
        }
        entity.setStatus(PayOrderStatus.CREATED.code());
        entity.setRefundAmount(BigDecimal.ZERO);
        try {
            payOrderMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付单号已存在: " + orderNo);
        }
        return toVo(payOrderMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO pay(Long id) {
        PayOrder existing = requireOrder(id);
        ensureStatus(existing, PayOrderStatus.CREATED.code(), "只有待支付的支付单可以支付");
        // 条件更新抢占 CREATED → PAID：并发下只有一个事务能成功，防止重复扣款
        claimTransition(id, PayOrderStatus.CREATED.code(), PayOrderStatus.PAID.code(), "只有待支付的支付单可以支付");
        existing.setStatus(PayOrderStatus.PAID.code());
        existing.setPayTime(LocalDateTime.now());
        PayChannelResult result = payChannelGateway.pay(existing);
        existing.setChannelTradeNo(result.providerTradeNo());
        payOrderMapper.updateById(existing);
        recordTransaction(existing, TYPE_PAY, result);
        com.bluewhale.pay.entity.PayNotifyTask task =
                payNotifyService.enqueue(PayNotifyEvent.PAID, existing, null, null);
        payNotifyService.dispatchAfterCommit(task == null ? null : task.getId());
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO cancel(Long id) {
        PayOrder existing = requireOrder(id);
        ensureStatus(existing, PayOrderStatus.CREATED.code(), "只有待支付的支付单可以取消");
        claimTransition(id, PayOrderStatus.CREATED.code(), PayOrderStatus.CANCELLED.code(), "只有待支付的支付单可以取消");
        existing.setStatus(PayOrderStatus.CANCELLED.code());
        payOrderMapper.updateById(existing);
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO refund(Long id) {
        if (payOrderMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        payRefundService.refundFull(id, "管理端整单退款");
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO createInternal(PayInternalCreateRequest request) {
        String orderNo = request.getOrderNo().trim();
        PayOrder existing = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOrderNo, orderNo));
        if (existing != null) {
            // 幂等创建：同单号重复创建直接返回原单，业务方重试是正常路径
            return toVo(existing);
        }
        PayOrderCreateRequest adminRequest = new PayOrderCreateRequest();
        adminRequest.setOrderNo(orderNo);
        adminRequest.setBusinessKey(request.getBizType());
        adminRequest.setAmount(request.getAmount());
        adminRequest.setCurrency(request.getCurrency());
        adminRequest.setChannel(request.getChannel());
        adminRequest.setRemark(request.getRemark());
        PayOrderVO created = create(adminRequest);
        PayOrder fresh = payOrderMapper.selectById(created.getId());
        fresh.setBizType(normalize(request.getBizType(), "MALL_ORDER"));
        fresh.setNotifyUrl(request.getNotifyUrl().trim());
        payOrderMapper.updateById(fresh);
        return toVo(payOrderMapper.selectById(created.getId()));
    }

    @Override
    public PayOrderVO getByOrderNo(String orderNo) {
        PayOrder order = requireOrderByNo(orderNo);
        return toVo(order);
    }

    @Override
    public PayOrderVO payByOrderNo(String orderNo) {
        return pay(requireOrderByNo(orderNo).getId());
    }

    @Override
    public PayOrderVO cancelByOrderNo(String orderNo) {
        PayOrder order = requireOrderByNo(orderNo);
        if (PayOrderStatus.CANCELLED.code().equals(order.getStatus())) {
            // 幂等取消：业务方重复取消直接成功
            return toVo(order);
        }
        return cancel(order.getId());
    }

    @Override
    public PayReconcileVO reconcile(Long id) {
        PayOrder existing = requireOrder(id);
        String channelStatus = payChannelGateway.queryStatus(existing);
        PayReconcileVO vo = new PayReconcileVO();
        vo.setOrderId(existing.getId());
        vo.setOrderNo(existing.getOrderNo());
        vo.setLocalStatus(existing.getStatus());
        vo.setChannelStatus(channelStatus);
        vo.setConsistent(Objects.equals(existing.getStatus(), channelStatus));
        vo.setProviderTradeNo(existing.getChannelTradeNo());
        vo.setCheckedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public List<PayTransactionVO> listTransactions(Long id) {
        requireOrder(id);
        LambdaQueryWrapper<PayTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayTransaction::getOrderId, id).orderByDesc(PayTransaction::getCreateTime);
        return payTransactionMapper.selectList(wrapper).stream()
                .map(PayOrderServiceImpl::toTransactionVo)
                .toList();
    }

    /**
     * 追加支付/退款成功流水
     *
     * @param order  支付单
     * @param type   流水类型
     * @param result 渠道结果
     */
    private void recordTransaction(PayOrder order, String type, PayChannelResult result) {
        PayTransaction transaction = new PayTransaction();
        transaction.setOrderId(order.getId());
        transaction.setOrderNo(order.getOrderNo());
        transaction.setType(type);
        transaction.setChannel(order.getChannel());
        transaction.setAmount(order.getAmount());
        transaction.setCurrency(order.getCurrency());
        transaction.setProviderTradeNo(result.providerTradeNo());
        transaction.setStatus(TX_SUCCESS);
        payTransactionMapper.insert(transaction);
    }

    /**
     * 查询支付单，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 支付单ID
     * @return 支付单实体
     */
    private PayOrder requireOrder(Long id) {
        PayOrder order = id == null ? null : payOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    /**
     * 按支付单号查询，不存在抛 NOT_FOUND。
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    private PayOrder requireOrderByNo(String orderNo) {
        PayOrder order = StringUtils.hasText(orderNo)
                ? payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, orderNo.trim()))
                : null;
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    /**
     * 状态前置校验，不满足则抛出 {@code PARAM_ERROR}
     *
     * @param order    支付单实体
     * @param expected 期望状态
     * @param message  错误提示
     */
    private static void ensureStatus(PayOrder order, String expected, String message) {
        if (!expected.equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 条件更新抢占状态流转：{@code update ... where id = ? and status = from}。
     * 依赖数据库行级锁保证并发下只有一个事务命中（影响行数为 1），
     * 未命中说明状态已被并发事务改变，直接抛 {@code PARAM_ERROR}；
     * 调用方处于事务内，后续渠道调用失败时抢占一并回滚。
     *
     * @param id      支付单ID
     * @param from    期望的当前状态
     * @param to      目标状态
     * @param message 抢占失败时的错误提示
     */
    private void claimTransition(Long id, String from, String to, String message) {
        LambdaUpdateWrapper<PayOrder> claim = new LambdaUpdateWrapper<>();
        claim.eq(PayOrder::getId, id)
                .eq(PayOrder::getStatus, from)
                .set(PayOrder::getStatus, to);
        if (payOrderMapper.update(null, claim) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 金额校验：必须大于 0 且最多 2 位小数
     *
     * @param amount 金额
     */
    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付金额必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付金额最多保留 2 位小数");
        }
    }

    /**
     * 唯一性校验：{@code order_no} 冲突时抛出 {@code PARAM_ERROR}
     *
     * @param orderNo 支付单号
     */
    private void ensureUnique(String orderNo) {
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrder::getOrderNo, orderNo);
        if (payOrderMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "支付单号已存在: " + orderNo);
        }
    }

    /**
     * 文本规范化：去空白并转大写，空值回落默认值
     *
     * @param value       原始值
     * @param defaultValue 默认值
     * @return 规范化后的值
     */
    private static String normalize(String value, String defaultValue) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
    }

    /**
     * 支付单实体转视图对象
     *
     * @param order 支付单实体
     * @return 支付单视图对象
     */
    private static PayOrderVO toVo(PayOrder order) {
        return PayOrderConverter.INSTANCE.toOrderVo(order);
    }

    /**
     * 流水实体转视图对象
     *
     * @param transaction 流水实体
     * @return 流水视图对象
     */
    private static PayTransactionVO toTransactionVo(PayTransaction transaction) {
        return PayOrderConverter.INSTANCE.toTransactionVo(transaction);
    }
}
