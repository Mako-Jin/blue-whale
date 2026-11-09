package com.bluewhale.mall.catalog.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.catalog.domain.model.Activity;
import com.bluewhale.mall.catalog.domain.model.ActivityStatus;
import com.bluewhale.mall.catalog.domain.model.MallMember;
import com.bluewhale.mall.catalog.domain.model.MemberStatus;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuPrice;
import com.bluewhale.mall.catalog.domain.model.StockInbound;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商城组合计价配套域模型：限时特价活动、会员登记、WMS 入库单、会员价解析。
 *
 * <p>这些域对象是组合计价链（券 + 活动 + 会员价）的输入侧，各自守住
 * 「不合法输入进不来、状态判定口径单一」两条底线。</p>
 *
 * @author BlueWhale
 */
class CatalogComboDomainTest {

    /** 活动校验：编码归一大写、价格规整两位、非法输入逐项拒绝 */
    @Test
    @DisplayName("活动校验与字段规整")
    void activityValidation() {
        Activity activity = activity("60.00");
        activity.setActivityCode(" act-001 ");
        activity.setActivityPrice(new BigDecimal("59.999"));
        activity.validate();

        assertThat(activity.getActivityCode()).isEqualTo("ACT-001");
        assertThat(activity.getActivityPrice()).isEqualByComparingTo("60.00");
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.ACTIVE);

        Activity noCode = activity("60.00");
        noCode.setActivityCode(" ");
        assertThatThrownBy(noCode::validate).isInstanceOf(BizException.class);

        Activity zeroPrice = activity("0.00");
        assertThatThrownBy(zeroPrice::validate).isInstanceOf(BizException.class);

        Activity noSku = activity("60.00");
        noSku.setSkuId(null);
        assertThatThrownBy(noSku::validate).isInstanceOf(BizException.class);

        Activity badPeriod = activity("60.00");
        badPeriod.setValidTo(badPeriod.getValidFrom().minusDays(1));
        assertThatThrownBy(badPeriod::validate).isInstanceOf(BizException.class);
    }

    /** 活动命中：时间窗内 + 启用 + 渠道匹配（空渠道 = 全渠道） */
    @Test
    @DisplayName("活动命中按时间窗、状态与渠道判定")
    void activityHits() {
        Activity activity = activity("60.00");
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);

        assertThat(activity.hits(now, 1L)).isTrue();
        assertThat(activity.hits(now, 2L)).isTrue();

        activity.setChannelId(1L);
        assertThat(activity.hits(now, 1L)).isTrue();
        assertThat(activity.hits(now, 2L)).isFalse();

        activity.setStatus(ActivityStatus.DISABLED);
        assertThat(activity.hits(now, 1L)).isFalse();

        activity.setStatus(ActivityStatus.ACTIVE);
        assertThat(activity.hits(now.minusDays(30), 1L)).isFalse();
        assertThat(activity.hits(now.plusDays(30), 1L)).isFalse();
    }

    /** 会员登记：缺省等级 GENERAL、缺省启用；effective 只认启用 + 有效期 */
    @Test
    @DisplayName("会员校验与生效判定")
    void memberValidateAndEffective() {
        MallMember member = new MallMember();
        member.setUserId(7L);
        member.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59));
        member.validate();

        assertThat(member.getLevelCode()).isEqualTo("GENERAL");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.effective(LocalDateTime.of(2026, 11, 9, 12, 0))).isTrue();
        assertThat(member.effective(LocalDateTime.of(2027, 1, 1, 0, 0))).isFalse();

        member.setValidTo(null);
        assertThat(member.effective(LocalDateTime.of(2027, 1, 1, 0, 0))).isTrue();

        member.setStatus(MemberStatus.DISABLED);
        assertThat(member.effective(LocalDateTime.of(2026, 11, 9, 12, 0))).isFalse();

        MallMember noUser = new MallMember();
        assertThatThrownBy(noUser::validate).isInstanceOf(BizException.class);
    }

    /** 状态枚举解析失败一律回落 DISABLED：未明确启用的不参与计价 */
    @Test
    @DisplayName("状态解析失败回落 DISABLED")
    void statusParseFallsBackDisabled() {
        assertThat(ActivityStatus.parse("bogus")).isEqualTo(ActivityStatus.DISABLED);
        assertThat(ActivityStatus.parse(null)).isEqualTo(ActivityStatus.DISABLED);
        assertThat(MemberStatus.parse("bogus")).isEqualTo(MemberStatus.DISABLED);
        assertThat(MemberStatus.parse("")).isEqualTo(MemberStatus.DISABLED);
    }

    /** 入库单校验：单号必填、来源缺省 WMS、行必须有 SKU 与正数量、编码归一大写 */
    @Test
    @DisplayName("WMS 入库单校验")
    void stockInboundValidation() {
        StockInbound inbound = new StockInbound();
        inbound.setDocNo(" WMS-20261109-001 ");
        inbound.getLines().add(line(101L, " sku-001 ", 5));
        inbound.validate();

        assertThat(inbound.getDocNo()).isEqualTo("WMS-20261109-001");
        assertThat(inbound.getSource()).isEqualTo("WMS");
        assertThat(inbound.getLines().get(0).getSkuCode()).isEqualTo("SKU-001");

        StockInbound noDoc = new StockInbound();
        noDoc.getLines().add(line(101L, "SKU-001", 5));
        assertThatThrownBy(noDoc::validate).isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.INBOUND_INVALID));

        StockInbound noLines = new StockInbound();
        noLines.setDocNo("WMS-1");
        assertThatThrownBy(noLines::validate).isInstanceOf(BizException.class);

        StockInbound bareLine = new StockInbound();
        bareLine.setDocNo("WMS-2");
        bareLine.getLines().add(line(null, null, 5));
        assertThatThrownBy(bareLine::validate).isInstanceOf(BizException.class);

        StockInbound badQty = new StockInbound();
        badQty.setDocNo("WMS-3");
        badQty.getLines().add(line(101L, "SKU-001", 0));
        assertThatThrownBy(badQty::validate).isInstanceOf(BizException.class);
    }

    /** 会员价解析：零售 + 会员命中 MEMBER 价目；非会员/缺价目/B2B 一律回落普通口径 */
    @Test
    @DisplayName("SKU 会员价解析与回落")
    void skuMemberPriceResolution() {
        Sku sku = sku("100.00", "80.00");

        assertThat(sku.resolvePrice(MallMode.RETAIL, true).getPrice()).isEqualByComparingTo("80.00");
        assertThat(sku.resolvePrice(MallMode.RETAIL, false).getPrice()).isEqualByComparingTo("100.00");

        Sku noMember = sku("100.00", null);
        assertThat(noMember.resolvePrice(MallMode.RETAIL, true).getPrice()).isEqualByComparingTo("100.00");

        // B2B 形态不吃会员价：无 B2B 价目时按兜底回落零售价目，而不是 MEMBER 价
        Sku b2bSku = sku("100.00", "80.00");
        assertThat(b2bSku.resolvePrice(MallMode.B2B, true).getPrice()).isEqualByComparingTo("100.00");
    }

    /** SKU 校验：单件重量必须大于 0，不计量留空 */
    @Test
    @DisplayName("SKU 重量校验")
    void skuWeightValidation() {
        Sku sku = sku("100.00", null);
        sku.setSkuCode("SKU-001");
        sku.setWeightKg(new BigDecimal("1.500"));
        sku.validate();
        assertThat(sku.getWeightKg()).isEqualByComparingTo("1.500");

        Sku badWeight = sku("100.00", null);
        badWeight.setSkuCode("SKU-002");
        badWeight.setWeightKg(BigDecimal.ZERO);
        assertThatThrownBy(badWeight::validate).isInstanceOf(BizException.class);
    }

    /**
     * 构造活动（缺省时间窗覆盖 2026-11）。
     *
     * @param price 活动价
     * @return 活动
     */
    private Activity activity(String price) {
        Activity activity = new Activity();
        activity.setActivityCode("ACT-001");
        activity.setActivityName("限时特价");
        activity.setSkuId(101L);
        activity.setActivityPrice(new BigDecimal(price));
        activity.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        activity.setValidTo(LocalDateTime.of(2026, 11, 30, 23, 59));
        activity.setStatus(ActivityStatus.ACTIVE);
        return activity;
    }

    /**
     * 构造入库单行。
     *
     * @param skuId   SKU ID（可空）
     * @param skuCode SKU 编码（可空）
     * @param qty     数量
     * @return 行
     */
    private StockInbound.StockInboundLine line(Long skuId, String skuCode, int qty) {
        StockInbound.StockInboundLine line = new StockInbound.StockInboundLine();
        line.setSkuId(skuId);
        line.setSkuCode(skuCode);
        line.setQuantity(qty);
        return line;
    }

    /**
     * 构造带价目的 SKU。
     *
     * @param retailPrice 零售价（null 时不配）
     * @param memberPrice 会员价（null 时不配）
     * @return SKU
     */
    private Sku sku(String retailPrice, String memberPrice) {
        Sku sku = new Sku();
        sku.setSkuCode("SKU-001");
        if (retailPrice != null) {
            SkuPrice retail = new SkuPrice();
            retail.setPriceType(PriceType.RETAIL);
            retail.setPrice(new BigDecimal(retailPrice));
            sku.getPrices().add(retail);
        }
        if (memberPrice != null) {
            SkuPrice member = new SkuPrice();
            member.setPriceType(PriceType.MEMBER);
            member.setPrice(new BigDecimal(memberPrice));
            sku.getPrices().add(member);
        }
        return sku;
    }
}
