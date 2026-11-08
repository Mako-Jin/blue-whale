package com.bluewhale.mall.aftersale.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.aftersale.application.command.AfterSaleApplyCommand;
import com.bluewhale.mall.aftersale.application.service.AfterSaleAppService;
import com.bluewhale.mall.aftersale.application.view.AfterSaleLineView;
import com.bluewhale.mall.aftersale.application.view.AfterSaleView;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleItem;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleStatus;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleType;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.aftersale.domain.repository.AfterSaleRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.coupon.application.service.CouponAppService;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderLog;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import com.bluewhale.mall.order.infrastructure.client.PayInternalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 售后应用服务实现。
 *
 * <p>三条不变量贯穿全流程：</p>
 * <ol>
 *   <li><b>金额只有一个算源</b>：{@code refundAmount} 由明细汇总，明细金额由订单行快照价 × 数量算，
 *       命令里传不了金额。</li>
 *   <li><b>额度只在数据库里判断</b>：行级可退数量走条件更新，先查再改在并发下必然超额。</li>
 *   <li><b>驳回和撤销必须还额度</b>：占了不还，商品被驳回一次就再也申请不了售后。</li>
 * </ol>
 *
 * <p>整单退款不单独实现，只是「覆盖全部可退行 + 直接通过审核」的一次应用，
 * 与买家申请走同一段代码。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AfterSaleAppServiceImpl implements AfterSaleAppService {

    /** 售后单号前缀 */
    private static final String NO_PREFIX = "AS";

    /** 售后单号时间段格式 */
    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 单号随机段长度 */
    private static final int RANDOM_SUFFIX_LEN = 4;

    /** 单号随机字符集（去掉易混淆的 I/O/0/1） */
    private static final String RANDOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 流水动作：申请售后 */
    private static final String ACTION_APPLY = "AFTER_SALE_APPLY";

    /** 流水动作：退款 */
    private static final String ACTION_REFUND = "REFUND";

    private final AfterSaleRepository afterSaleRepository;

    private final OrderRepository orderRepository;

    /** whale-pay 内网客户端（服务间直连） */
    private final PayInternalClient payInternalClient;

    /** 优惠券应用服务（整单退满后退券） */
    private final CouponAppService couponAppService;

    private final MallProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(AfterSaleApplyCommand command, MallActor actor) {
        Long userId = actor.requireUserId();
        MallOrder order = requireOrder(command.getOrderId());
        if (!order.belongsTo(userId)) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_NOT_OWNER);
        }
        assertRefundable(order);
        List<AfterSaleItem> items = buildItems(order, command.getLines());
        MallAfterSale afterSale = MallAfterSale.create(newAfterSaleNo(), order.getId(), order.getOrderNo(),
                userId, parseType(command.getType()), command.getReason(), command.getDescription(),
                command.getImages(), items);
        afterSale.setReturnLogisticsNo(command.getReturnLogisticsNo());
        occupyQuota(afterSale, order);
        afterSaleRepository.insert(afterSale);
        orderRepository.appendLog(order.getId(), actorLog(ACTION_APPLY, statusName(order), statusName(order),
                actor, "申请售后 " + afterSale.getAfterSaleNo() + "，金额 " + afterSale.getRefundAmount()));
        return afterSale.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String remark, MallActor actor) {
        requireOperator(actor, "只有商城运营可以审核售后");
        MallAfterSale afterSale = requireAfterSale(id);
        MallOrder order = requireOrder(afterSale.getOrderId());
        afterSale.approve(actor.getUserId(), actor.getUsername(), remark);
        assertRemainingAmount(order, afterSale.getRefundAmount());
        afterSaleRepository.update(afterSale);
        orderRepository.appendLog(order.getId(), actorLog(ACTION_APPLY, statusName(order), statusName(order),
                actor, "售后 " + afterSale.getAfterSaleNo() + " 审核通过，发起退款"));
        if (!StringUtils.hasText(order.getPayNo())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_REFUND_FAILED, "订单尚未创建支付单，无法退款");
        }
        // 远端失败抛异常回滚，售后单状态与行额度占用一并撤销
        payInternalClient.refund(order.getPayNo(), afterSale.getAfterSaleNo(),
                afterSale.getRefundAmount(), afterSale.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark, MallActor actor) {
        requireOperator(actor, "只有商城运营可以驳回售后");
        MallAfterSale afterSale = requireAfterSale(id);
        afterSale.reject(actor.getUserId(), actor.getUsername(), remark);
        afterSaleRepository.update(afterSale);
        releaseQuota(afterSale);
        // 驳回不改订单状态，轨迹的起止状态都取订单当前状态；
        // to_status 是 NOT NULL 列，写 null 会让整笔驳回事务回滚
        MallOrder order = requireOrder(afterSale.getOrderId());
        orderRepository.appendLog(order.getId(), actorLog(ACTION_APPLY, statusName(order), statusName(order),
                actor, "售后 " + afterSale.getAfterSaleNo() + " 已驳回：" + (remark == null ? "" : remark)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, MallActor actor) {
        Long userId = actor.requireUserId();
        MallAfterSale afterSale = requireAfterSale(id);
        if (!userId.equals(afterSale.getUserId()) && !actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_NOT_OWNER);
        }
        afterSale.cancel();
        afterSaleRepository.update(afterSale);
        releaseQuota(afterSale);
        // 同上：撤销不改订单状态，但不能给 NOT NULL 的 to_status 写 null
        MallOrder order = requireOrder(afterSale.getOrderId());
        orderRepository.appendLog(order.getId(), actorLog(ACTION_APPLY, statusName(order), statusName(order),
                actor, "售后 " + afterSale.getAfterSaleNo() + " 已撤销"));
    }

    @Override
    public PageResult<AfterSaleView> page(String status, String keyword, Long current, Long size,
                                          boolean manageAll, MallActor actor) {
        Long userId;
        if (manageAll) {
            requireOperator(actor, "只有商城运营可以查看全部售后");
            userId = null;
        } else {
            userId = actor.requireUserId();
        }
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        List<MallAfterSale> records = afterSaleRepository.page(userId, status, keyword,
                (pageNo - 1L) * pageSize, (int) pageSize);
        List<AfterSaleView> views = new ArrayList<>(records.size());
        for (MallAfterSale afterSale : records) {
            views.add(toView(afterSale));
        }
        return PageResult.of(views, afterSaleRepository.count(userId, status, keyword), pageNo, pageSize);
    }

    @Override
    public AfterSaleView detail(Long id, MallActor actor) {
        MallAfterSale afterSale = requireAfterSale(id);
        Long userId = actor.requireUserId();
        if (!userId.equals(afterSale.getUserId()) && !actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_NOT_OWNER);
        }
        return toView(afterSale);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String applyFullRefund(MallOrder order, String reason, MallActor actor) {
        requireOperator(actor, "只有商城运营可以发起整单退款");
        assertRefundable(order);
        if (!StringUtils.hasText(order.getPayNo())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_REFUND_FAILED, "订单尚未创建支付单，无法退款");
        }
        List<AfterSaleApplyCommand.ApplyLine> lines = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            int refundable = item.refundableQuantity();
            if (refundable > 0) {
                AfterSaleApplyCommand.ApplyLine line = new AfterSaleApplyCommand.ApplyLine();
                line.setOrderItemId(item.getId());
                line.setQuantity(refundable);
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED, "该订单已无可退商品");
        }
        List<AfterSaleItem> items = buildItems(order, lines);
        MallAfterSale afterSale = MallAfterSale.create(newAfterSaleNo(), order.getId(), order.getOrderNo(),
                order.getBuyerUserId(), AfterSaleType.REFUND_ONLY,
                StringUtils.hasText(reason) ? reason : "整单退款", "管理端整单退款", null, items);
        afterSale.approve(actor.getUserId(), actor.getUsername(), "管理端整单退款");
        occupyQuota(afterSale, order);
        afterSaleRepository.insert(afterSale);
        String from = statusName(order);
        order.requestRefund();
        orderRepository.update(order);
        orderRepository.appendLog(order.getId(), actorLog(ACTION_REFUND, from, statusName(order), actor,
                "整单退款 " + afterSale.getAfterSaleNo() + "，金额 " + afterSale.getRefundAmount()));
        payInternalClient.refund(order.getPayNo(), afterSale.getAfterSaleNo(),
                afterSale.getRefundAmount(), afterSale.getReason());
        return afterSale.getAfterSaleNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRefunded(String orderNo, String requestNo, BigDecimal paidBackTotal) {
        MallOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND));
        Optional<MallAfterSale> found = afterSaleRepository.findByAfterSaleNo(requestNo);
        if (found.isEmpty()) {
            markRefundedWithoutAfterSale(order, requestNo, paidBackTotal);
            return;
        }
        MallAfterSale afterSale = found.get();
        if (afterSale.getStatus() == AfterSaleStatus.REFUNDED) {
            // 通知会重试，已退款幂等成功
            return;
        }
        afterSale.markRefunded(requestNo, LocalDateTime.now());
        afterSaleRepository.update(afterSale);
        String from = statusName(order);
        if (!afterSaleRepository.applyOrderRefund(order.getId(), afterSale.getRefundAmount())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_REFUND_FAILED,
                    "订单累计退款失败：可退金额不足或状态不允许");
        }
        // 退款是条件更新，订单是否转 REFUNDED 取决于累计退款额，必须回读真实状态写入轨迹；
        // to_status 是 NOT NULL 列，留空会让整笔回调事务回滚、支付方无限重投
        MallOrder refunded = orderRepository.findById(order.getId()).orElse(order);
        orderRepository.appendLog(order.getId(), systemLog(ACTION_REFUND, from, statusName(refunded),
                "退款回调 " + afterSale.getAfterSaleNo() + "，本次 " + afterSale.getRefundAmount()));
        releaseCouponIfFullyRefunded(order);
    }

    /**
     * 整单退满后把券退回未使用。
     *
     * <p>只在退满时退券：部分退款时优惠已经按分摊折进了已退金额，
     * 再把整张券还回去，等于让买家拿同一张券退两次钱。</p>
     *
     * @param order 订单（退款累计后的状态以库中为准）
     */
    private void releaseCouponIfFullyRefunded(MallOrder order) {
        if (order.getCouponUserId() == null) {
            return;
        }
        MallOrder refreshed = orderRepository.findById(order.getId()).orElse(order);
        if (refreshed.getStatus() == OrderStatus.REFUNDED) {
            couponAppService.releaseByOrderNo(refreshed.getOrderNo());
        }
    }

    /**
     * 兼容分支：回调指向的售后单不存在（M1 在途退款单）。
     *
     * <p>按支付侧累计退款额推算本次增量，只累计订单退款金额，不抛错。
     * 抛错会让投递方一直重试到死信队列，而这类老单子上线瞬间最容易出现。</p>
     *
     * @param order         订单
     * @param requestNo     业务退款请求号
     * @param paidBackTotal 支付侧累计退款额
     */
    private void markRefundedWithoutAfterSale(MallOrder order, String requestNo, BigDecimal paidBackTotal) {
        BigDecimal already = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        BigDecimal total = paidBackTotal == null ? order.getTotalAmount() : paidBackTotal;
        BigDecimal delta = total.subtract(already);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String from = statusName(order);
        if (!afterSaleRepository.applyOrderRefund(order.getId(), delta)) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_REFUND_FAILED,
                    "订单累计退款失败：可退金额不足或状态不允许");
        }
        MallOrder refunded = orderRepository.findById(order.getId()).orElse(order);
        orderRepository.appendLog(order.getId(), systemLog(ACTION_REFUND, from, statusName(refunded),
                "退款回调（无售后单，请求号 " + requestNo + "），本次 " + delta));
        releaseCouponIfFullyRefunded(order);
    }

    /**
     * 按命令构造售后明细。
     *
     * <p>行必须属于该订单，同一行不能重复提交（否则一次申请就能把额度翻倍占用）。
     * 数量先做一次可读的校验，真正的兜底仍在条件更新上。</p>
     *
     * @param order 订单
     * @param lines 申请行
     * @return 售后明细
     */
    private List<AfterSaleItem> buildItems(MallOrder order, List<AfterSaleApplyCommand.ApplyLine> lines) {
        Map<Long, OrderItem> byId = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            byId.put(item.getId(), item);
        }
        Set<Long> seen = new HashSet<>();
        List<AfterSaleItem> result = new ArrayList<>(lines.size());
        for (AfterSaleApplyCommand.ApplyLine line : lines) {
            OrderItem orderItem = byId.get(line.getOrderItemId());
            if (orderItem == null) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_NOT_FOUND, "订单中不存在该商品行");
            }
            if (!seen.add(line.getOrderItemId())) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID, "同一商品行不能重复提交");
            }
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity <= 0) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID, "退数量必须大于 0");
            }
            if (quantity > orderItem.refundableQuantity()) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED,
                        orderItem.getSpuName() + " 可退 " + orderItem.refundableQuantity() + " 件");
            }
            result.add(toAfterSaleItem(orderItem, quantity));
        }
        return result;
    }

    /**
     * 用订单行实付额构造售后行（金额在此算出，不接受外部传入）。
     *
     * @param orderItem 订单行
     * @param quantity  本次退数量
     * @return 售后行
     */
    private AfterSaleItem toAfterSaleItem(OrderItem orderItem, int quantity) {
        AfterSaleItem item = new AfterSaleItem();
        item.setOrderItemId(orderItem.getId());
        item.setSpuId(orderItem.getSpuId());
        item.setSkuId(orderItem.getSkuId());
        item.setSpuName(orderItem.getSpuName());
        item.setSkuCode(orderItem.getSkuCode());
        item.setUnitPrice(orderItem.getUnitPrice());
        item.setQuantity(quantity);
        item.setAmount(refundableShare(orderItem, quantity));
        return item;
    }

    /**
     * 本次退数量对应的可退金额。
     *
     * <p>基数用行实付额（小计 - 分摊优惠 + 分摊运费）而不是快照价：
     * 按快照价退，一张 8 折券的订单会退给买家两成现金。</p>
     *
     * <p>分次退同一行时，最后一次直接取「剩余可退金额」而不是再按比例算一遍：
     * 三次各按比例四舍五入，合计常常差一分钱，而对账时这一分钱要查半天。</p>
     *
     * @param orderItem 订单行
     * @param quantity  本次退数量
     * @return 本次退款金额
     */
    private BigDecimal refundableShare(OrderItem orderItem, int quantity) {
        int total = orderItem.getQuantity() == null ? 0 : orderItem.getQuantity();
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        int refunded = orderItem.getRefundedQuantity() == null ? 0 : orderItem.getRefundedQuantity();
        if (refunded + quantity >= total) {
            return orderItem.refundableAmount();
        }
        return orderItem.netAmount()
                .multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(total), AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 占用全部行的退款额度，任一失败即整体回滚。
     *
     * @param afterSale 售后单
     * @param order     订单
     */
    private void occupyQuota(MallAfterSale afterSale, MallOrder order) {
        for (AfterSaleItem item : afterSale.getItems()) {
            if (!afterSaleRepository.occupyItemQuota(item.getOrderItemId(), item.getQuantity(), item.getAmount())) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED,
                        "商品「" + item.getSpuName() + "」可退数量不足，可能有其他售后单正在处理");
            }
        }
        if (afterSale.getRefundAmount().compareTo(order.getTotalAmount()) > 0) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_AMOUNT_EXCEEDED, "退款金额超过订单总额");
        }
    }

    /**
     * 释还全部行的退款额度（驳回或撤销时）。
     *
     * @param afterSale 售后单
     */
    private void releaseQuota(MallAfterSale afterSale) {
        for (AfterSaleItem item : afterSale.getItems()) {
            afterSaleRepository.releaseItemQuota(item.getOrderItemId(), item.getQuantity(), item.getAmount());
        }
    }

    /**
     * 校验订单剩余可退金额够不够本次申请。
     *
     * @param order  订单
     * @param amount 本次退款金额
     */
    private void assertRemainingAmount(MallOrder order, BigDecimal amount) {
        BigDecimal already = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        BigDecimal remaining = order.getTotalAmount().subtract(already);
        if (amount.compareTo(remaining) > 0) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_AMOUNT_EXCEEDED,
                    "订单剩余可退 " + remaining + "，不足以支付本次 " + amount);
        }
    }

    /**
     * 校验订单当前状态可退。
     *
     * @param order 订单
     */
    private void assertRefundable(MallOrder order) {
        if (order.getStatus() == null || !order.getStatus().canRefund()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_REFUND_NOT_ALLOWED,
                    "订单当前状态（" + order.getStatus() + "）不可退款");
        }
    }

    /**
     * 校验操作人是商城运营。
     *
     * @param actor   操作人
     * @param message 失败信息
     */
    private void requireOperator(MallActor actor, String message) {
        actor.requireUserId();
        if (!actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, message);
        }
    }

    /**
     * 查询订单，不存在即失败。
     *
     * @param id 订单 ID
     * @return 订单
     */
    private MallOrder requireOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 查询售后单，不存在即失败。
     *
     * @param id 售后单 ID
     * @return 售后单
     */
    private MallAfterSale requireAfterSale(Long id) {
        return afterSaleRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.AFTER_SALE_NOT_FOUND));
    }

    /**
     * 解析售后类型，缺省为仅退款。
     *
     * @param value 类型文本
     * @return 售后类型
     */
    private AfterSaleType parseType(String value) {
        if (!StringUtils.hasText(value)) {
            return AfterSaleType.REFUND_ONLY;
        }
        AfterSaleType type = AfterSaleType.parse(value);
        if (type == null) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID, "售后类型不合法：" + value);
        }
        return type;
    }

    /**
     * 生成售后单号：AS + 时间戳 + 随机段（唯一键兜底）。
     *
     * @return 售后单号
     */
    private static String newAfterSaleNo() {
        StringBuilder random = new StringBuilder(RANDOM_SUFFIX_LEN);
        for (int i = 0; i < RANDOM_SUFFIX_LEN; i++) {
            random.append(RANDOM_ALPHABET.charAt(RANDOM.nextInt(RANDOM_ALPHABET.length())));
        }
        return NO_PREFIX + LocalDateTime.now().format(NO_TIME) + random;
    }

    /**
     * 状态名（空安全）。
     *
     * @param order 订单
     * @return 状态名
     */
    private String statusName(MallOrder order) {
        return order.getStatus() == null ? null : order.getStatus().name();
    }

    /**
     * 构造操作人流水。
     *
     * @param action 动作
     * @param from   原状态
     * @param to     新状态
     * @param actor  操作人
     * @param remark 说明
     * @return 流水
     */
    private OrderLog actorLog(String action, String from, String to, MallActor actor, String remark) {
        OrderLog log = new OrderLog();
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(actor.getUserId());
        log.setOperatorName(actor.getUsername());
        log.setRemark(remark);
        return log;
    }

    /**
     * 构造系统流水。
     *
     * @param action 动作
     * @param from   原状态
     * @param to     新状态
     * @param remark 说明
     * @return 流水
     */
    private OrderLog systemLog(String action, String from, String to, String remark) {
        OrderLog log = new OrderLog();
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorName("SYSTEM");
        log.setRemark(remark);
        return log;
    }

    /**
     * 售后单转视图。
     *
     * @param afterSale 售后单
     * @return 视图
     */
    private AfterSaleView toView(MallAfterSale afterSale) {
        AfterSaleView view = new AfterSaleView();
        view.setId(afterSale.getId());
        view.setAfterSaleNo(afterSale.getAfterSaleNo());
        view.setOrderId(afterSale.getOrderId());
        view.setOrderNo(afterSale.getOrderNo());
        view.setUserId(afterSale.getUserId());
        view.setType(afterSale.getType() == null ? null : afterSale.getType().name());
        view.setStatus(afterSale.getStatus() == null ? null : afterSale.getStatus().name());
        view.setRefundAmount(afterSale.getRefundAmount());
        view.setReason(afterSale.getReason());
        view.setDescription(afterSale.getDescription());
        view.setImages(afterSale.getImages());
        view.setAuditUserId(afterSale.getAuditUserId());
        view.setAuditUserName(afterSale.getAuditUserName());
        view.setAuditTime(afterSale.getAuditTime());
        view.setAuditRemark(afterSale.getAuditRemark());
        view.setRefundNo(afterSale.getRefundNo());
        view.setRefundTime(afterSale.getRefundTime());
        view.setReturnLogisticsNo(afterSale.getReturnLogisticsNo());
        view.setCreateTime(afterSale.getCreateTime());
        for (AfterSaleItem item : afterSale.getItems()) {
            AfterSaleLineView line = new AfterSaleLineView();
            line.setId(item.getId());
            line.setOrderItemId(item.getOrderItemId());
            line.setSpuId(item.getSpuId());
            line.setSkuId(item.getSkuId());
            line.setSpuName(item.getSpuName());
            line.setSkuCode(item.getSkuCode());
            line.setUnitPrice(item.getUnitPrice());
            line.setQuantity(item.getQuantity());
            line.setAmount(item.getAmount());
            view.getItems().add(line);
        }
        return view;
    }
}
