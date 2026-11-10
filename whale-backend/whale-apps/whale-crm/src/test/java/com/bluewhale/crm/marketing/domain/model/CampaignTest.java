package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 营销活动聚合不变量测试。
 *
 * @author BlueWhale
 */
@DisplayName("营销活动聚合")
class CampaignTest {

    @Test
    @DisplayName("草稿活动不能直接结束（非法流转）")
    void draftCannotEnd() {
        Campaign campaign = new Campaign();
        campaign.setStatus(CampaignStatus.DRAFT);
        assertThrows(BizException.class, campaign::end);
    }

    @Test
    @DisplayName("已结束活动不能再次启动（非法流转）")
    void endedCannotStart() {
        Campaign campaign = new Campaign();
        campaign.setStatus(CampaignStatus.ENDED);
        assertThrows(BizException.class, campaign::start);
    }

    @Test
    @DisplayName("预算为负数被拒绝")
    void negativeBudgetRejected() {
        assertThrows(BizException.class, () -> Campaign.validateBudget(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("结束日期早于开始日期被拒绝")
    void invalidPeriodRejected() {
        assertThrows(BizException.class, () -> Campaign.validatePeriod(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
    }

    @Test
    @DisplayName("实际费用为 0 时 ROI 为 0")
    void roiWithZeroCost() {
        Campaign campaign = new Campaign();
        campaign.setActualCost(BigDecimal.ZERO);
        campaign.setAttributedWonAmount(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("0.0000"), campaign.recalculateRoi());
    }

    @Test
    @DisplayName("ROI 按归因赢单金额除以实际费用计算")
    void roiCalculated() {
        Campaign campaign = new Campaign();
        campaign.setActualCost(new BigDecimal("100.00"));
        campaign.setAttributedWonAmount(new BigDecimal("200.00"));
        assertEquals(new BigDecimal("2.0000"), campaign.recalculateRoi());
    }
}
