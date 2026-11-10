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

    // ------------------------------------------------------------------
    // 举证图片校验（服务端归一化）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("图片归一化：去空、去重、保留有效 URL")
    void createNormalizesImages() {
        MallAfterSale afterSale = MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, "质量问题", "描述",
                " http://a.png , http://b.png ,,http://a.png, ", pendingItems());

        assertThat(afterSale.getImages()).isEqualTo("http://a.png,http://b.png");
    }

    @Test
    @DisplayName("图片归一化：空白串归为 null")
    void createBlankImagesAsNull() {
        MallAfterSale afterSale = MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, "质量问题", "描述", " , ,, ", pendingItems());

        assertThat(afterSale.getImages()).isNull();
    }

    @Test
    @DisplayName("图片校验：超过 6 张拒绝")
    void createRejectsTooManyImages() {
        String images = "http://1.png,http://2.png,http://3.png,http://4.png,http://5.png,http://6.png,http://7.png";

        assertThatThrownBy(() -> MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, "质量问题", "描述", images, pendingItems()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_INVALID));
    }

    @Test
    @DisplayName("图片校验：单张 URL 超过 500 字符拒绝")
    void createRejectsOverlongImageUrl() {
        String longUrl = "http://example.com/" + "a".repeat(500);

        assertThatThrownBy(() -> MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, "质量问题", "描述", longUrl, pendingItems()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_INVALID));
    }

    // ------------------------------------------------------------------
    // 退货闭环新状态机（WAIT_RETURN / EXCHANGING / EXCHANGED）
    // ------------------------------------------------------------------

    /**
     * 构造指定类型的待审核售后单。
     *
     * @param type 售后类型
     * @return 售后单
     */
    private MallAfterSale pendingOfType(AfterSaleType type) {
        return MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                type, "质量问题", "描述", null, pendingItems());
    }

    /**
     * 构造一条售后明细（19.90 × 2）。
     *
     * @return 明细列表
     */
    private List<AfterSaleItem> pendingItems() {
        List<AfterSaleItem> items = new ArrayList<>();
        items.add(item(11L, "19.90", 2));
        return items;
    }

    @Test
    @DisplayName("退货退款审核通过：进入待退货而不是退款执行中")
    void approveReturnRefundEntersWaitReturn() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.RETURN_REFUND);

        afterSale.approve(99L, "运营甲", "同意");

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.WAIT_RETURN);
        assertThatThrownBy(afterSale::cancel).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("仅退款审核通过：仍直接进入退款执行中（原路径不变）")
    void approveRefundOnlyStaysApproved() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.REFUND_ONLY);

        afterSale.approve(99L, "运营甲", "同意");

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.APPROVED);
    }

    @Test
    @DisplayName("回填退货物流：待退货可回填并清洗首尾空白")
    void fillReturnLogisticsFromWaitReturn() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.RETURN_REFUND);
        afterSale.approve(99L, "运营甲", "同意");

        afterSale.fillReturnLogistics("  SF123456789  ");

        assertThat(afterSale.getReturnLogisticsNo()).isEqualTo("SF123456789");
    }

    @Test
    @DisplayName("回填退货物流：单号为空或超长拒绝")
    void fillReturnLogisticsRejectsBlankOrOverlong() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.RETURN_REFUND);
        afterSale.approve(99L, "运营甲", "同意");

        assertThatThrownBy(() -> afterSale.fillReturnLogistics("  "))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_INVALID));
        assertThatThrownBy(() -> afterSale.fillReturnLogistics("a".repeat(65)))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_INVALID));
    }

    @Test
    @DisplayName("回填退货物流：待审核与已通过状态拒绝")
    void fillReturnLogisticsRejectsWrongState() {
        MallAfterSale created = pendingOfType(AfterSaleType.RETURN_REFUND);
        assertThatThrownBy(() -> created.fillReturnLogistics("SF123")).isInstanceOf(BizException.class);

        MallAfterSale approved = pendingOfType(AfterSaleType.RETURN_REFUND);
        approved.approve(99L, "运营甲", "同意");
        approved.confirmReturn();
        assertThatThrownBy(() -> approved.fillReturnLogistics("SF123")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("确认收货：退货退款回到退款执行中，换货进入补发等待位")
    void confirmReturnBranchesByType() {
        MallAfterSale returnRefund = pendingOfType(AfterSaleType.RETURN_REFUND);
        returnRefund.approve(99L, "运营甲", "同意");
        returnRefund.confirmReturn();
        assertThat(returnRefund.getStatus()).isEqualTo(AfterSaleStatus.APPROVED);

        MallAfterSale exchange = pendingOfType(AfterSaleType.EXCHANGE);
        exchange.approve(99L, "运营甲", "同意");
        assertThat(exchange.getStatus()).isEqualTo(AfterSaleStatus.WAIT_RETURN);
        exchange.confirmReturn();
        assertThat(exchange.getStatus()).isEqualTo(AfterSaleStatus.EXCHANGING);
    }

    @Test
    @DisplayName("确认收货：待审核状态拒绝（货还没批给退）")
    void confirmReturnRejectsCreated() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.RETURN_REFUND);

        assertThatThrownBy(afterSale::confirmReturn)
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_STATUS_INVALID));
    }

    @Test
    @DisplayName("标记换货完成：补发中 → 已换货终态；非补发中拒绝")
    void markExchangedTerminal() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.EXCHANGE);
        afterSale.approve(99L, "运营甲", "同意");

        // 待退货状态不能直接结到已换货（必须先确认收货、补发发货）
        assertThatThrownBy(afterSale::markExchanged).isInstanceOf(BizException.class);

        afterSale.confirmReturn();
        afterSale.markExchanged();
        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.EXCHANGED);
        assertThat(afterSale.getStatus().isFinal()).isTrue();
    }

    @Test
    @DisplayName("驳回：待退货也可驳（买家寄错货），已通过不可驳")
    void rejectAllowsWaitReturn() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.RETURN_REFUND);
        afterSale.approve(99L, "运营甲", "同意");

        afterSale.reject(99L, "运营甲", "寄错商品");
        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.REJECTED);

        MallAfterSale approved = pendingOfType(AfterSaleType.REFUND_ONLY);
        approved.approve(99L, "运营甲", "同意");
        assertThatThrownBy(() -> approved.reject(99L, "运营甲", "晚了一步"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("撤销：换货在待退货可撤（没占额度），退货退款在待退货不可撤")
    void cancelExchangeInWaitReturnOnly() {
        MallAfterSale exchange = pendingOfType(AfterSaleType.EXCHANGE);
        exchange.approve(99L, "运营甲", "同意");
        exchange.cancel();
        assertThat(exchange.getStatus()).isEqualTo(AfterSaleStatus.CANCELLED);

        MallAfterSale returnRefund = pendingOfType(AfterSaleType.RETURN_REFUND);
        returnRefund.approve(99L, "运营甲", "同意");
        assertThatThrownBy(returnRefund::cancel).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("超时自动关闭：仅待审核可关，待退货不可关（买家可能已寄回）")
    void expireCancelOnlyFromCreated() {
        MallAfterSale afterSale = pendingOfType(AfterSaleType.RETURN_REFUND);
        afterSale.expireCancel();
        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.CANCELLED);

        MallAfterSale waiting = pendingOfType(AfterSaleType.RETURN_REFUND);
        waiting.approve(99L, "运营甲", "同意");
        assertThatThrownBy(waiting::expireCancel).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("标记退款完成：待退货状态拒绝（货还没收），审核通过照常")
    void markRefundedStillRequiresApproved() {
        MallAfterSale waiting = pendingOfType(AfterSaleType.RETURN_REFUND);
        waiting.approve(99L, "运营甲", "同意");

        assertThatThrownBy(() -> waiting.markRefunded("AS20261107000001", null))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_STATUS_INVALID));

        waiting.confirmReturn();
        waiting.markRefunded("AS20261107000001", null);
        assertThat(waiting.getStatus()).isEqualTo(AfterSaleStatus.REFUNDED);
    }

    @Test
    @DisplayName("类型解析：换货需退回商品且标记为换货")
    void exchangeTypeSemantics() {
        assertThat(AfterSaleType.parse("exchange")).isEqualTo(AfterSaleType.EXCHANGE);
        assertThat(AfterSaleType.EXCHANGE.requiresReturn()).isTrue();
        assertThat(AfterSaleType.EXCHANGE.isExchange()).isTrue();
        assertThat(AfterSaleType.REFUND_ONLY.isExchange()).isFalse();
        assertThat(AfterSaleStatus.parse("wait_return")).isEqualTo(AfterSaleStatus.WAIT_RETURN);
        assertThat(AfterSaleStatus.parse("exchanging")).isEqualTo(AfterSaleStatus.EXCHANGING);
        assertThat(AfterSaleStatus.WAIT_RETURN.isActiveExchangeState()).isTrue();
        assertThat(AfterSaleStatus.EXCHANGED.isActiveExchangeState()).isFalse();
        assertThat(AfterSaleStatus.EXCHANGED.canFillReturnLogistics()).isFalse();
        assertThat(AfterSaleStatus.WAIT_RETURN.canFillReturnLogistics()).isTrue();
    }
}
