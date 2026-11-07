package com.bluewhale.mall.aftersale.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleItem;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleStatus;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleType;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.common.MallErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MallAfterSale} 状态机与金额汇总测试。
 *
 * <p>重点覆盖三件事：金额只能由明细汇总、非法状态不能跳转、回调重复到达必须幂等。</p>
 *
 * @author BlueWhale
 */
class MallAfterSaleTest {

    /**
     * 构造一条售后明细。
     *
     * @param orderItemId 订单明细 ID
     * @param unitPrice   单价
     * @param quantity    数量
     * @return 明细
     */
    private AfterSaleItem item(long orderItemId, String unitPrice, int quantity) {
        AfterSaleItem item = new AfterSaleItem();
        item.setOrderItemId(orderItemId);
        item.setSpuName("测试商品" + orderItemId);
        item.setSkuCode("SKU-" + orderItemId);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        item.setAmount(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    /**
     * 构造待审核售后单。
     *
     * @return 售后单
     */
    private MallAfterSale pending() {
        List<AfterSaleItem> items = new ArrayList<>();
        items.add(item(11L, "19.90", 2));
        return MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, "质量问题", "描述", null, items);
    }

    @Test
    @DisplayName("创建：金额由明细汇总，状态为待审核")
    void createSumsAmount() {
        MallAfterSale afterSale = pending();

        assertThat(afterSale.getRefundAmount()).isEqualByComparingTo("39.80");
        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.CREATED);
        assertThat(afterSale.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("创建：明细为空直接拒绝")
    void createRejectsEmptyItems() {
        assertThatThrownBy(() -> MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, null, null, null, List.of()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_INVALID));
    }

    @Test
    @DisplayName("审核通过：落审核人与时间")
    void approveFillsAudit() {
        MallAfterSale afterSale = pending();

        afterSale.approve(99L, "运营甲", "同意");

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.APPROVED);
        assertThat(afterSale.getAuditUserId()).isEqualTo(99L);
        assertThat(afterSale.getAuditUserName()).isEqualTo("运营甲");
        assertThat(afterSale.getAuditRemark()).isEqualTo("同意");
        assertThat(afterSale.getAuditTime()).isNotNull();
    }

    @Test
    @DisplayName("审核通过：已通过的单不能重复通过")
    void approveTwiceRejected() {
        MallAfterSale afterSale = pending();
        afterSale.approve(99L, "运营甲", "同意");

        assertThatThrownBy(() -> afterSale.approve(99L, "运营乙", "再同意一次"))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_STATUS_INVALID));
    }

    @Test
    @DisplayName("驳回与撤销：只能从待审核出发")
    void rejectAndCancelOnlyFromCreated() {
        MallAfterSale rejected = pending();
        rejected.reject(99L, "运营甲", "不符合条件");
        assertThat(rejected.getStatus()).isEqualTo(AfterSaleStatus.REJECTED);
        assertThat(rejected.getStatus().isFinal()).isTrue();
        assertThatThrownBy(rejected::cancel).isInstanceOf(BizException.class);

        MallAfterSale cancelled = pending();
        cancelled.cancel();
        assertThat(cancelled.getStatus()).isEqualTo(AfterSaleStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.reject(99L, "运营甲", "晚了一步"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("已通过的单不能撤销：钱已在路上，撤销会让账面凭空多出钱")
    void cancelRejectedAfterApprove() {
        MallAfterSale afterSale = pending();
        afterSale.approve(99L, "运营甲", "同意");

        assertThatThrownBy(afterSale::cancel)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_STATUS_INVALID));
    }

    @Test
    @DisplayName("标记退款完成：审核通过 → 已退款，落退款时间")
    void markRefundedFromApproved() {
        MallAfterSale afterSale = pending();
        afterSale.approve(99L, "运营甲", "同意");

        afterSale.markRefunded("AS20261107000001", LocalDateTime.now());

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.REFUNDED);
        assertThat(afterSale.getRefundTime()).isNotNull();
        assertThat(afterSale.getStatus().isFinal()).isTrue();
    }

    @Test
    @DisplayName("标记退款完成：已退款重复回调幂等返回，不报错")
    void markRefundedIsIdempotent() {
        MallAfterSale afterSale = pending();
        afterSale.approve(99L, "运营甲", "同意");
        afterSale.markRefunded("AS20261107000001", null);
        LocalDateTime first = afterSale.getRefundTime();

        afterSale.markRefunded("AS20261107000001", LocalDateTime.now().plusMinutes(5));

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.REFUNDED);
        // 幂等返回不刷新时间，避免重试把完成时间越推越晚
        assertThat(afterSale.getRefundTime()).isEqualTo(first);
    }

    @Test
    @DisplayName("标记退款完成：待审核的单不能跳过审核直接退款")
    void markRefundedRejectsCreated() {
        MallAfterSale afterSale = pending();

        assertThatThrownBy(() -> afterSale.markRefunded("AS20261107000001", null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_STATUS_INVALID));
    }

    @Test
    @DisplayName("是否覆盖整单：按退款金额与订单总额比对")
    void coversWholeOrder() {
        MallAfterSale afterSale = pending();

        assertThat(afterSale.coversWholeOrder(new BigDecimal("39.80"))).isTrue();
        assertThat(afterSale.coversWholeOrder(new BigDecimal("100.00"))).isFalse();
        assertThat(afterSale.coversWholeOrder(null)).isFalse();
    }

    @Test
    @DisplayName("类型解析：未知值返回 null，退货退款需退回商品")
    void parseType() {
        assertThat(AfterSaleType.parse("return_refund")).isEqualTo(AfterSaleType.RETURN_REFUND);
        assertThat(AfterSaleType.parse("unknown")).isNull();
        assertThat(AfterSaleType.RETURN_REFUND.requiresReturn()).isTrue();
        assertThat(AfterSaleType.REFUND_ONLY.requiresReturn()).isFalse();
    }

    @Test
    @DisplayName("状态解析：未知值返回 null")
    void parseStatus() {
        assertThat(AfterSaleStatus.parse("approved")).isEqualTo(AfterSaleStatus.APPROVED);
        assertThat(AfterSaleStatus.parse(" ")).isNull();
        assertThat(AfterSaleStatus.parse("nope")).isNull();
    }
}
