package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.config.PayReconcileProperties;
import com.bluewhale.pay.converter.PayOrderConverter;
import com.bluewhale.pay.dto.PayInternalCreateRequest;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayTransactionPo;
import com.bluewhale.pay.enums.PayCloseReason;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.mapper.stat.PayStaleOrderPo;
import com.bluewhale.pay.service.PayOrderService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.service.PaySettlementService;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayReconcileVO;
import com.bluewhale.pay.vo.PayTransactionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
 * 状态流转单向：{@code CREATED → PAYING → PAID / CANCELLED}、{@code PAID → REFUNDED}。
 * 状态流转通过「条件更新抢占」（{@code update ... where status = 期望状态}）保证并发安全：
 * 同一支付单的并发支付/取消/退款只有一个事务能赢得抢占，其余直接失败，
 * 从而杜绝重复扣款、重复退款与重复流水。</p>
 *
 * <p><b>渠道调用与事务边界（P0-2）：</b>支付拆成「抢占提交 → 事务外调渠道 → 记账提交」三段。
 * 抢占 {@code CREATED → PAYING} 先提交，渠道网络往返不持有数据库事务；渠道成功后的记账失败
 * 会让订单停在 {@code PAYING}，由对账暴露并人工确认，而不是静默回滚成「钱扣了、账没记」。
 * 渠道明确失败时把 {@code PAYING} 回退为 {@code CREATED}，允许业务方重试。
 * 支付与退款成功后追加 {@code pay_transaction} 流水，并回填渠道流水号与退款信息；
 * 对账把本地状态与渠道状态比对后返回一致性结果。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements PayOrderService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;


    /** 默认渠道：mock */
    private static final String DEFAULT_CHANNEL = "MOCK";

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 默认业务方类型 */
    private static final String DEFAULT_BIZ_TYPE = "MALL_ORDER";

    /** 金额最大小数位 */
    private static final int MAX_AMOUNT_SCALE = 2;

    private final PayOrderMapper payOrderMapper;

    private final PayTransactionMapper payTransactionMapper;

    private final PayChannelGateway payChannelGateway;

    private final PayRefundService payRefundService;

    private final PaySettlementService paySettlementService;

    private final TransactionTemplate transactionTemplate;

    private final PayReconcileProperties reconcileProperties;

    @Override
    public PageResult<PayOrderVO> page(PayOrderQueryRequest request) {
        LambdaQueryWrapper<PayOrderPo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(inner -> inner.like(PayOrderPo::getOrderNo, request.getKeyword())
                    .or().like(PayOrderPo::getBusinessKey, request.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(request.getStatus()), PayOrderPo::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getChannel()), PayOrderPo::getChannel, request.getChannel());
        wrapper.orderByDesc(PayOrderPo::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PayOrderPo> page = payOrderMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
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
        PayOrderPo entity = PayOrderConverter.INSTANCE.toEntity(request);
        entity.setOrderNo(orderNo);
        entity.setCurrency(normalize(request.getCurrency(), DEFAULT_CURRENCY));
        entity.setChannel(normalize(request.getChannel(), DEFAULT_CHANNEL));
        // 白名单 + 适配器同时存在才允许创建，避免建出永远付不了的单
        payChannelGateway.ensureAvailable(entity.getChannel());
        entity.setStatus(PayOrderStatus.CREATED.code());
        entity.setRefundAmount(BigDecimal.ZERO);
        try {
            payOrderMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.no-duplicate",
                    "支付单号已存在: " + orderNo, orderNo);
        }
        return toVo(payOrderMapper.selectById(entity.getId()));
    }

    @Override
    public PayOrderVO pay(Long id) {
        PayOrderPo existing = requireOrder(id);
        ensurePayable(existing);
        /* ① 抢占事务：CREATED → PAYING 并立即提交。先落「支付中」再调渠道，
           渠道调用期间不持有数据库事务/连接；进程崩溃后订单停在 PAYING，
           可由对账发现，而不是留下「渠道已扣款、本地仍 CREATED」的静默不一致。 */
        transactionTemplate.executeWithoutResult(status ->
                claimTransition(id, PayOrderStatus.CREATED.code(), PayOrderStatus.PAYING.code(),
                        "只有待支付的支付单可以支付"));
        existing.setStatus(PayOrderStatus.PAYING.code());
        PayChannelResult result;
        try {
            // ② 事务外调用渠道：真实渠道的网络往返不再受本地事务时长与回滚影响
            result = payChannelGateway.pay(existing);
        } catch (RuntimeException ex) {
            // 渠道明确失败：回退 PAYING → CREATED，允许业务方重试
            transactionTemplate.executeWithoutResult(status ->
                    releasePaymentClaim(id, ex.getMessage()));
            throw ex;
        }
        /* ③ 记账：渠道即时成功才落账；受理中保持 PAYING，等回调或对账确认。
           本段失败时订单保持 PAYING（渠道可能已扣款，不能回退），由对账兜底。 */
        paySettlementService.markPaid(existing, result);
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO cancel(Long id) {
        PayOrderPo existing = requireOrder(id);
        ensureStatus(existing, PayOrderStatus.CREATED.code(), "只有待支付的支付单可以取消");
        claimTransition(id, PayOrderStatus.CREATED.code(), PayOrderStatus.CANCELLED.code(), "只有待支付的支付单可以取消");
        existing.setStatus(PayOrderStatus.CANCELLED.code());
        existing.setCloseReason(PayCloseReason.MANUAL.code());
        payOrderMapper.updateById(existing);
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    public PayOrderVO refund(Long id) {
        // 不包事务：渠道调用与退款记账的事务边界由 PayRefundService 内部三段式控制
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
        validateAmount(request.getAmount());
        validateNotifyUrl(request.getNotifyUrl());
        PayOrderPo existing = findByOrderNo(orderNo);
        if (existing != null) {
            // 幂等创建：同单号重复创建直接返回原单，业务方重试是正常路径
            return toVo(existing);
        }
        // bizType/notifyUrl 在首次插入即写入，避免「创建后补写」造成幂等返回缺字段
        PayOrderPo entity = new PayOrderPo();
        entity.setOrderNo(orderNo);
        entity.setBizType(normalize(request.getBizType(), DEFAULT_BIZ_TYPE));
        entity.setNotifyUrl(request.getNotifyUrl().trim());
        entity.setAmount(request.getAmount());
        entity.setCurrency(normalize(request.getCurrency(), DEFAULT_CURRENCY));
        entity.setChannel(normalize(request.getChannel(), DEFAULT_CHANNEL));
        entity.setRemark(request.getRemark());
        entity.setStatus(PayOrderStatus.CREATED.code());
        entity.setRefundAmount(BigDecimal.ZERO);
        payChannelGateway.ensureAvailable(entity.getChannel());
        try {
            payOrderMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            // 并发同单号：后到请求回读已存在原单，保持幂等语义
            PayOrderPo concurrent = findByOrderNo(orderNo);
            if (concurrent != null) {
                return toVo(concurrent);
            }
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.no-duplicate",
                    "支付单号已存在: " + orderNo, orderNo);
        }
        return toVo(payOrderMapper.selectById(entity.getId()));
    }

    @Override
    public PayOrderVO getByOrderNo(String orderNo) {
        PayOrderPo order = requireOrderByNo(orderNo);
        return toVo(order);
    }

    @Override
    public PayOrderVO payByOrderNo(String orderNo) {
        return pay(requireOrderByNo(orderNo).getId());
    }

    @Override
    public PayOrderVO cancelByOrderNo(String orderNo) {
        PayOrderPo order = requireOrderByNo(orderNo);
        if (PayOrderStatus.CANCELLED.code().equals(order.getStatus())) {
            // 幂等取消：业务方重复取消直接成功
            return toVo(order);
        }
        return cancel(order.getId());
    }

    @Override
    public PayReconcileVO reconcile(Long id) {
        PayOrderPo existing = requireOrder(id);
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

    /**
     * 自动对账停留单：跨租户扫描 → 逐单还原租户上下文 → 查询渠道 → 仅 PAID 收敛。
     *
     * <p>定时线程没有租户上下文，租户 SQL 插件会整体忽略（既不加条件也不带 tenant_id 写入），
     * 因此必须由扫描结果显式设置租户，否则补写的流水与通知任务会落到默认租户。</p>
     *
     * @return 本轮成功收敛的笔数
     */
    @Override
    @Scheduled(fixedDelayString = "${whale.pay.reconcile.interval-ms:60000}")
    public int reconcileStalePaying() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(reconcileProperties.getStaleSeconds());
        List<PayStaleOrderPo> stale = payOrderMapper.selectStalePaying(before, reconcileProperties.getBatchSize());
        int converged = 0;
        for (PayStaleOrderPo item : stale) {
            TenantContext.set(item.getTenantId());
            try {
                if (convergeIfChannelPaid(item.getId())) {
                    converged++;
                }
            } catch (RuntimeException ex) {
                // 单笔异常不能中断整轮：订单保持 PAYING，留待下一轮或人工确认
                log.error("PAYING 停留单对账异常: id={} tenantId={}", item.getId(), item.getTenantId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (converged > 0 && log.isInfoEnabled()) {
            log.info("PAYING 对账轮次完成: stale={} converged={}", stale.size(), converged);
        }
        return converged;
    }

    /**
     * 单笔停留单对账：渠道明确 PAID 才在独立事务内收敛，其余状态不回退。
     *
     * @param id 支付单 ID
     * @return 是否由本次调用收敛为 PAID
     */
    private boolean convergeIfChannelPaid(Long id) {
        PayOrderPo order = payOrderMapper.selectById(id);
        if (order == null || !PayOrderStatus.PAYING.code().equals(order.getStatus())) {
            // 扫描后已被其他线程推进或删除，跳过
            return false;
        }
        String channelStatus;
        try {
            channelStatus = payChannelGateway.queryStatus(order);
        } catch (RuntimeException ex) {
            log.error("渠道状态查询失败，支付单保持 PAYING: orderNo={} channel={}",
                    order.getOrderNo(), order.getChannel(), ex);
            return false;
        }
        if (!PayOrderStatus.PAID.code().equals(channelStatus)) {
            // 渠道可能状态滞后：回退成 CREATED 会允许重复支付，宁可保持 PAYING 等人工确认
            log.warn("渠道状态非 PAID，支付单保持 PAYING 待人工确认: orderNo={} channelStatus={}",
                    order.getOrderNo(), channelStatus);
            return false;
        }
        // 与同步支付、渠道回调共用同一落账入口：条件抢占 PAYING → PAID，避免重复流水
        return paySettlementService.markPaid(order, new PayChannelResult(order.getChannelTradeNo()));
    }

    @Override
    public List<PayTransactionVO> listTransactions(Long id) {
        requireOrder(id);
        LambdaQueryWrapper<PayTransactionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayTransactionPo::getOrderId, id).orderByDesc(PayTransactionPo::getCreateTime);
        return payTransactionMapper.selectList(wrapper).stream()
                .map(PayOrderServiceImpl::toTransactionVo)
                .toList();
    }

    /**
     * 查询支付单，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 支付单ID
     * @return 支付单实体
     */
    private PayOrderPo requireOrder(Long id) {
        PayOrderPo order = id == null ? null : payOrderMapper.selectById(id);
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
    private PayOrderPo requireOrderByNo(String orderNo) {
        PayOrderPo order = StringUtils.hasText(orderNo)
                ? payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrderPo>().eq(PayOrderPo::getOrderNo, orderNo.trim()))
                : null;
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    /**
     * 按支付单号查询，不存在返回 {@code null}（幂等判断用）。
     *
     * @param orderNo 支付单号
     * @return 支付单或 null
     */
    private PayOrderPo findByOrderNo(String orderNo) {
        return payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrderPo>()
                .eq(PayOrderPo::getOrderNo, orderNo));
    }

    /**
     * 状态前置校验，不满足则抛出 {@code PARAM_ERROR}
     *
     * @param order    支付单实体
     * @param expected 期望状态
     * @param message  错误提示
     */
    private static void ensureStatus(PayOrderPo order, String expected, String message) {
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
     * <p>流转合法性由 {@link PayOrderStatus#canTransitionTo} 判定：新增状态时只改枚举，
     * 不用在服务里逐处补分支。</p>
     *
     * @param id      支付单ID
     * @param from    期望的当前状态
     * @param to      目标状态
     * @param message 抢占失败时的错误提示
     */
    private void claimTransition(Long id, String from, String to, String message) {
        PayOrderStatus fromStatus = PayOrderStatus.fromCode(from);
        PayOrderStatus toStatus = PayOrderStatus.fromCode(to);
        if (fromStatus == null || toStatus == null || !fromStatus.canTransitionTo(toStatus)) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
        LambdaUpdateWrapper<PayOrderPo> claim = new LambdaUpdateWrapper<>();
        claim.eq(PayOrderPo::getId, id)
                .eq(PayOrderPo::getStatus, from)
                .set(PayOrderPo::getStatus, to)
                // 同步推进 update_time：PAYING 的停留时长从抢占时刻算起，
                // 否则老单刚进入 PAYING 就会被对账当成停留单提前介入
                .set(PayOrderPo::getUpdateTime, LocalDateTime.now());
        if (payOrderMapper.update(null, claim) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 支付状态前置校验：PAYING 视为处理中，不重复发起渠道请求。
     *
     * @param order 支付单实体
     */
    private static void ensurePayable(PayOrderPo order) {
        if (PayOrderStatus.PAYING.code().equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.paying",
                    "支付处理中，请稍后重试或通过对账确认结果");
        }
        ensureStatus(order, PayOrderStatus.CREATED.code(), "只有待支付的支付单可以支付");
    }

    /**
     * 渠道明确失败后回退抢占：{@code PAYING → CREATED}，允许业务方重试。
     *
     * <p>回退本身失败（如数据库不可用）只会让订单停在 PAYING，由对账兜底，
     * 不会造成资金重复动作；因此这里记录错误而不覆盖原始渠道异常。</p>
     *
     * @param id     支付单ID
     * @param reason 渠道失败原因
     */
    private void releasePaymentClaim(Long id, String reason) {
        LambdaUpdateWrapper<PayOrderPo> release = new LambdaUpdateWrapper<>();
        release.eq(PayOrderPo::getId, id)
                .eq(PayOrderPo::getStatus, PayOrderStatus.PAYING.code())
                .set(PayOrderPo::getStatus, PayOrderStatus.CREATED.code());
        if (payOrderMapper.update(null, release) == 0) {
            log.warn("渠道失败后未能回退 PAYING→CREATED，需人工对账: id={} reason={}", id, reason);
        }
    }

    /**
     * 金额校验：必须大于 0 且最多 2 位小数
     *
     * @param amount 金额
     */
    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.amount-invalid", "支付金额必须大于 0");
        }
        if (amount.stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.amount-scale-invalid", "支付金额最多保留 2 位小数");
        }
    }

    /**
     * 回调地址格式校验：必须是 {@code <服务名>/<路径>}，非法值在创建阶段直接拒绝，
     * 避免落库后到投递阶段才失败并一路重试至 DEAD。
     *
     * @param notifyUrl 回调地址
     */
    private static void validateNotifyUrl(String notifyUrl) {
        if (PayNotifyServiceImpl.splitNotifyUrl(notifyUrl == null ? null : notifyUrl.trim()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.notify-url-invalid",
                    "回调地址格式非法，应为 <服务名>/<路径>: " + notifyUrl, notifyUrl);
        }
    }

    /**
     * 唯一性校验：{@code order_no} 冲突时抛出 {@code PARAM_ERROR}
     *
     * @param orderNo 支付单号
     */
    private void ensureUnique(String orderNo) {
        LambdaQueryWrapper<PayOrderPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrderPo::getOrderNo, orderNo);
        if (payOrderMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.order.no-duplicate",
                    "支付单号已存在: " + orderNo, orderNo);
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
    private static PayOrderVO toVo(PayOrderPo order) {
        PayOrderVO vo = PayOrderConverter.INSTANCE.toOrderVo(order);
        vo.setRefundableAmount(refundableOf(order));
        return vo;
    }

    /**
     * 剩余可退金额：仅已支付单可退，退款后退完为 0。
     *
     * @param order 支付单
     * @return 剩余可退金额
     */
    private static BigDecimal refundableOf(PayOrderPo order) {
        if (!PayOrderStatus.PAID.code().equals(order.getStatus())) {
            return BigDecimal.ZERO;
        }
        BigDecimal refunded = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        BigDecimal remaining = order.getAmount().subtract(refunded);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    /**
     * 流水实体转视图对象
     *
     * @param transaction 流水实体
     * @return 流水视图对象
     */
    private static PayTransactionVO toTransactionVo(PayTransactionPo transaction) {
        return PayOrderConverter.INSTANCE.toTransactionVo(transaction);
    }
}
