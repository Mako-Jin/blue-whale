package com.bluewhale.mall.aftersale.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 售后单（聚合根）。
 *
 * <p>售后单承载「谁申请、退哪几行、审核意见、退款进度」。整单退款不是独立流程，
 * 而是它的一条特例——覆盖全部可退行、审核直接通过。这样退款只有一条实现路径，
 * 行级额度占用、幂等键、回调结单全部复用，不会出现「整单退走 A 分支、部分退走 B 分支」
 * 两套逻辑各修各的 bug。</p>
 *
 * <p>{@code refundAmount} 由明细汇总得出，不接受外部传入：让前端传金额和让前端传价格
 * 是同一类错误——金额只能有一个计算来源。</p>
 *
 * @author BlueWhale
 */
@Data
public class MallAfterSale {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

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

    /** 本次申请退款金额 */
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
     * @param afterSaleNo 售后单号
     * @param orderId     订单 ID
     * @param orderNo     订单号
     * @param userId      买家用户 ID
     * @param type        售后类型
     * @param reason      售后原因
     * @param description 问题描述
     * @param images      凭证图片
     * @param items       售后明细（已带成交单价与数量）
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
        afterSale.setImages(images);
        afterSale.setItems(items);
        afterSale.setRefundAmount(sumAmount(items));
        return afterSale;
    }

    /**
     * 审核通过（待审核 → 审核通过）。
     *
     * @param auditUserId   审核人 ID
     * @param auditUserName 审核人名称
     * @param remark        审核意见
     */
    public void approve(Long auditUserId, String auditUserName, String remark) {
        assertStatus(AfterSaleStatus.CREATED, "只有待审核的售后单可以通过");
        this.status = AfterSaleStatus.APPROVED;
        fillAudit(auditUserId, auditUserName, remark);
    }

    /**
     * 驳回（待审核 → 已驳回）。
     *
     * @param auditUserId   审核人 ID
     * @param auditUserName 审核人名称
     * @param remark        驳回原因
     */
    public void reject(Long auditUserId, String auditUserName, String remark) {
        assertStatus(AfterSaleStatus.CREATED, "只有待审核的售后单可以驳回");
        this.status = AfterSaleStatus.REJECTED;
        fillAudit(auditUserId, auditUserName, remark);
    }

    /**
     * 撤销（待审核 → 已撤销）。
     */
    public void cancel() {
        assertStatus(AfterSaleStatus.CREATED, "只有待审核的售后单可以撤销");
        this.status = AfterSaleStatus.CANCELLED;
    }

    /**
     * 标记已退款（审核通过 → 已退款）。
     *
     * <p>已是已退款状态时直接返回：支付侧通知会重试，重复处理不能报错——
     * 报错会让投递方一直重试到死信队列。</p>
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
