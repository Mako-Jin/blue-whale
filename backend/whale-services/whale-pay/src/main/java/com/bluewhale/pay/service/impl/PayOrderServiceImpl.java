package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.entity.PayTransaction;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.service.PayOrderService;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayReconcileVO;
import com.bluewhale.pay.vo.PayTransactionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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

    /** 状态：待支付 */
    private static final String STATUS_CREATED = "CREATED";

    /** 状态：已支付 */
    private static final String STATUS_PAID = "PAID";

    /** 状态：已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 状态：已退款 */
    private static final String STATUS_REFUNDED = "REFUNDED";

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
        PayOrder entity = new PayOrder();
        BeanUtils.copyProperties(request, entity);
        entity.setOrderNo(orderNo);
        entity.setCurrency(normalize(request.getCurrency(), DEFAULT_CURRENCY));
        entity.setChannel(normalize(request.getChannel(), DEFAULT_CHANNEL));
        if (!payChannelGateway.isSupported(entity.getChannel())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的支付渠道: " + entity.getChannel());
        }
        entity.setStatus(STATUS_CREATED);
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
        ensureStatus(existing, STATUS_CREATED, "只有待支付的支付单可以支付");
        PayChannelResult result = payChannelGateway.pay(existing);
        existing.setStatus(STATUS_PAID);
        existing.setPayTime(LocalDateTime.now());
        existing.setChannelTradeNo(result.providerTradeNo());
        payOrderMapper.updateById(existing);
        recordTransaction(existing, TYPE_PAY, result);
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO cancel(Long id) {
        PayOrder existing = requireOrder(id);
        ensureStatus(existing, STATUS_CREATED, "只有待支付的支付单可以取消");
        existing.setStatus(STATUS_CANCELLED);
        payOrderMapper.updateById(existing);
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO refund(Long id) {
        PayOrder existing = requireOrder(id);
        ensureStatus(existing, STATUS_PAID, "只有已支付的支付单可以退款");
        PayChannelResult result = payChannelGateway.refund(existing);
        existing.setStatus(STATUS_REFUNDED);
        existing.setRefundAmount(existing.getAmount());
        existing.setRefundTime(LocalDateTime.now());
        payOrderMapper.updateById(existing);
        recordTransaction(existing, TYPE_REFUND, result);
        return toVo(payOrderMapper.selectById(id));
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
        PayOrderVO vo = new PayOrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    /**
     * 流水实体转视图对象
     *
     * @param transaction 流水实体
     * @return 流水视图对象
     */
    private static PayTransactionVO toTransactionVo(PayTransaction transaction) {
        PayTransactionVO vo = new PayTransactionVO();
        BeanUtils.copyProperties(transaction, vo);
        return vo;
    }
}
