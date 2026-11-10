package com.bluewhale.mall.aftersale.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 售后单（聚合根）。
 *
 * <p>售后单承载「谁申请、退哪几行、审核意见、退款进度」。整单退款不是独立流程，
 * 而是它的一条特例——覆盖全部可退行、审核直接通过。这样退款只有一条实现路径，
 * 行级额度占用、幂等键、回调结单全部复用，不会出现「整单退走 A 分支、部分退走 B 分支」
 * 两套逻辑各修各的 bug。</p>
 *
 * <p>{@code refundAmount} 由明细汇总得出，不接受外部传入：让前端传金额和让前端传价格
 * 是同一类错误——金额只能有一个计算来源。换货单是例外：换货不退钱，明细金额记 0，
 * 不占退款额度，防超占改用「同一订单行进行中换货单查重」。</p>
 *
 * <p>状态机按类型分流：仅退款审核即退款；退货类（退货退款 / 换货）审核通过先进
 * {@code WAIT_RETURN}，买家寄回、运营确认收货后才分别走退款或补发。</p>
 *
 * @author BlueWhale
 */
@Data
public class MallAfterSale {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 凭证图片上限（张） */
    public static final int IMAGE_MAX_COUNT = 6;

    /** 单张图片 URL 长度上限 */
    public static final int IMAGE_URL_MAX_LENGTH = 500;

    /** 退货物流单号长度上限（对齐表列 VARCHAR(64)） */
    private static final int RETURN_LOGISTICS_MAX_LENGTH = 64;

    /** 主键 */
    private Long id;

    /** 售后单号（同时作支付侧退款幂等键） */
    private String afterSaleNo;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 买家用户 ID */
    private Long userId;

    /** 类型 */
    private AfterSaleType type;

    /** 状态 */
    private AfterSaleStatus status;

    /** 本次申请退款金额（换货单恒为 0） */
    private BigDecimal refundAmount;

    /** 售后原因 */
    private String reason;

    /** 问题描述 */
    private String description;

    /** 凭证图片 URL（逗号分隔） */
    private String images;

    /** 审核人 ID */
    private Long auditUserId;

    /** 审核人名称 */
    private String auditUserName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见 */
    private String auditRemark;

    /** 支付侧退款流水号 */
    private String refundNo;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 退回物流单号 */
    private String returnLogisticsNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 售后明细 */
    private List<AfterSaleItem> items = new ArrayList<>();

    /**
     * 创建售后单（待审核）。
     *
     * <p>凭证图片在此归一化：去空、去重、限 6 张、单张 URL 限长——
     * 校验放领域模型而不是控制器，任何入口（含未来的内网接口）都不会漏掉。</p>
     *
     * @param afterSaleNo 售后单号
     * @param orderId     订单 ID
     * @param orderNo     订单号
     * @param userId      买家用户 ID
     * @param type        售后类型
     * @param reason      售后原因
     * @param description 问题描述
     * @param images      凭证图片
     * @param items       售后明细（已带成交单价与数量；换货单金额须已记 0）
     * @return 售后单
     */
    public static MallAfterSale create(String afterSaleNo, Long orderId, String orderNo, Long userId,
                                       AfterSaleType type, String reason, String description,
                                       String images, List<AfterSaleItem> items) {
        if (items == null || items.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID, "售后明细不能为空");
        }
        MallAfterSale afterSale = new MallAfterSale();
        afterSale.setAfterSaleNo(afterSaleNo);
        afterSale.setOrderId(orderId);
        afterSale.setOrderNo(orderNo);
        afterSale.setUserId(userId);
        afterSale.setType(type);
        afterSale.setStatus(AfterSaleStatus.CREATED);
        afterSale.setReason(reason);
        afterSale.setDescription(description);
        afterSale.setImages(normalizeImages(images));
        afterSale.setItems(items);
        afterSale.setRefundAmount(sumAmount(items));
        return afterSale;
    }

    /**
     * 审核通过。
     *
     * <p>按类型分流：仅退款直接进入「退款执行中」；退货类先进「待退货」，
     * 钱和货只能先动一个——买家还没寄回货就发起退款，等于凭空多退一份。</p>
     *
     * @param auditUserId   审核人 ID
     * @param auditUserName 审核人名称
     * @param remark        审核意见
     */
    public void approve(Long auditUserId, String auditUserName, String remark) {
        assertStatus(AfterSaleStatus.CREATED, "只有待审核的售后单可以通过");
        this.status = type != null && type.requiresReturn()
                ? AfterSaleStatus.WAIT_RETURN : AfterSaleStatus.APPROVED;
        fillAudit(auditUserId, auditUserName, remark);
    }

    /**
     * 驳回（待审核或待退货 → 已驳回）。
     *
     * <p>待退货也允许驳回：买家填错了物流、寄丢了货，运营驳回让买家重新申请，
     * 比让一张单永远卡在待退货里好。额度由应用层释放。</p>
     *
     * @param auditUserId   审核人 ID
     * @param auditUserName 审核人名称
     * @param remark        驳回原因
     */
    public void reject(Long auditUserId, String auditUserName, String remark) {
        assertStatusAnyOf(Set.of(AfterSaleStatus.CREATED, AfterSaleStatus.WAIT_RETURN),
                "只有待审核或待退货的售后单可以驳回");
        this.status = AfterSaleStatus.REJECTED;
        fillAudit(auditUserId, auditUserName, remark);
    }

    /**
     * 撤销（待审核 → 已撤销；换货单在待退货也允许）。
     *
     * <p>退款类售后在待退货阶段不可撤销：额度已占、买家可能已寄货，
     * 撤销只能由运营驳回。换货单不占退款额度，待退货阶段撤销不留悬账。</p>
     */
    public void cancel() {
        boolean exchangeInReturn = this.status == AfterSaleStatus.WAIT_RETURN && type != null && type.isExchange();
        if (this.status != AfterSaleStatus.CREATED && !exchangeInReturn) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_STATUS_INVALID,
                    "只有待审核的售后单可以撤销（换货单在待退货阶段也可撤销）");
        }
        this.status = AfterSaleStatus.CANCELLED;
    }

    /**
     * 超时自动关闭（待审核 → 已撤销）。
     *
     * <p>只关待审核：待退货、换货补发中说明买家可能已经把货寄出去了，
     * 自动关掉会让在途包裹悬空。额度释放与流水由应用层完成。</p>
     */
    public void expireCancel() {
        assertStatus(AfterSaleStatus.CREATED, "只有待审核的售后单可以超时自动关闭");
        this.status = AfterSaleStatus.CANCELLED;
    }

    /**
     * 回填退货物流单号（待退货 → 待退货）。
     *
     * <p>买家寄回商品后补录运单号；只对待退货开放——审核通过即退款的旧路径上
     * 不存在「等货」这回事，APPROVED 老单走原退款回调收尾。</p>
     *
     * @param logisticsNo 退货物流单号
     */
    public void fillReturnLogistics(String logisticsNo) {
        assertStatus(AfterSaleStatus.WAIT_RETURN, "只有待退货的售后单可以回填退货物流");
        if (logisticsNo == null || logisticsNo.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID, "退货物流单号不能为空");
        }
        String trimmed = logisticsNo.trim();
        if (trimmed.length() > RETURN_LOGISTICS_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID,
                    "退货物流单号不能超过 " + RETURN_LOGISTICS_MAX_LENGTH + " 字符");
        }
        this.returnLogisticsNo = trimmed;
    }

    /**
     * 确认收到退货（待退货 → 审核通过 / 换货补发中）。
     *
     * <p>按类型分流：退货退款回到「退款执行中」走既有退款路径；
     * 换货进入「换货补发中」，等运营建补发发货单。</p>
     */
    public void confirmReturn() {
        assertStatus(AfterSaleStatus.WAIT_RETURN, "只有待退货的售后单可以确认收货");
        this.status = type != null && type.isExchange()
                ? AfterSaleStatus.EXCHANGING : AfterSaleStatus.APPROVED;
    }

    /**
     * 标记换货完成（换货补发中 → 已换货，终态）。
     *
     * <p>补发发货单创建成功后调用：发货占数量、扣库存与终态必须同事务，
     * 否则会出现「发货单在了但售后单还挂着」的悬账。</p>
     */
    public void markExchanged() {
        assertStatus(AfterSaleStatus.EXCHANGING, "只有换货补发中的售后单可以标记换货完成");
        this.status = AfterSaleStatus.EXCHANGED;
    }

    /**
     * 标记已退款（审核通过 → 已退款）。
     *
     * <p>已是已退款状态时直接返回：支付侧通知会重试，重复处理不能报错——
     * 报错会让投递方一直重试到死信队列。只认审核通过：待退货说明货还没收，
     * 这时到达的退款通知是异常投递，直接报错暴露问题。</p>
     *
     * @param payRefundNo 支付侧退款流水号
     * @param refundedAt  退款完成时间
     */
    public void markRefunded(String payRefundNo, LocalDateTime refundedAt) {
        if (this.status == AfterSaleStatus.REFUNDED) {
            return;
        }
        assertStatus(AfterSaleStatus.APPROVED, "只有审核通过的售后单可以标记退款完成");
        this.status = AfterSaleStatus.REFUNDED;
        this.refundNo = payRefundNo;
        this.refundTime = refundedAt == null ? LocalDateTime.now() : refundedAt;
    }

    /**
     * 是否覆盖整张订单（用于判定整单退款）。
     *
     * @param orderTotal 订单总额
     * @return 退款金额等于订单总额返回 true
     */
    public boolean coversWholeOrder(BigDecimal orderTotal) {
        return orderTotal != null && refundAmount != null && refundAmount.compareTo(orderTotal) == 0;
    }

    /**
     * 校验当前状态，不符抛业务异常。
     *
     * @param expected 期望状态
     * @param message  异常信息
     */
    private void assertStatus(AfterSaleStatus expected, String message) {
        if (this.status != expected) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_STATUS_INVALID, message);
        }
    }

    /**
     * 校验当前状态属于任一允许状态，不符抛业务异常。
     *
     * @param expected 期望状态集合
     * @param message  异常信息
     */
    private void assertStatusAnyOf(Set<AfterSaleStatus> expected, String message) {
        if (this.status == null || !expected.contains(this.status)) {
            throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_STATUS_INVALID, message);
        }
    }

    /**
     * 填充审核信息。
     *
     * @param auditUserId   审核人 ID
     * @param auditUserName 审核人名称
     * @param remark        审核意见
     */
    private void fillAudit(Long auditUserId, String auditUserName, String remark) {
        this.auditUserId = auditUserId;
        this.auditUserName = auditUserName;
        this.auditRemark = remark;
        this.auditTime = LocalDateTime.now();
    }

    /**
     * 归一化凭证图片：去空、去重、限张数、限单张长度。
     *
     * <p>入参是逗号分隔的 URL 串。不去重的话前端控件抖一下同一张图就提交两遍；
     * 不限张数的话一张售后单能挂一屏图片把列表页拖死。</p>
     *
     * @param images 原始图片串
     * @return 归一化后的图片串（无有效图片返回 null）
     */
    private static String normalizeImages(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String part : images.split(",")) {
            String url = part == null ? "" : part.trim();
            if (url.isEmpty()) {
                continue;
            }
            if (url.length() > IMAGE_URL_MAX_LENGTH) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID,
                        "单张图片 URL 不能超过 " + IMAGE_URL_MAX_LENGTH + " 字符");
            }
            distinct.add(url);
            if (distinct.size() > IMAGE_MAX_COUNT) {
                throw MallErrorCode.exception(MallErrorCode.AFTER_SALE_INVALID,
                        "凭证图片最多 " + IMAGE_MAX_COUNT + " 张");
            }
        }
        if (distinct.isEmpty()) {
            return null;
        }
        return String.join(",", distinct);
    }

    /**
     * 汇总明细金额。
     *
     * @param items 明细
     * @return 合计金额
     */
    private static BigDecimal sumAmount(List<AfterSaleItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (AfterSaleItem item : items) {
            BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO
                    : item.getAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            total = total.add(amount);
        }
        return total.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
