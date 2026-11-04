package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MpsPlan} 单元测试。
 *
 * @author BlueWhale
 */
class MpsPlanTest {

    @Test
    @DisplayName("汇总计划数量")
    void shouldCalculateTotal() {
        MpsPlan plan = newPlan();

        plan.calculateTotal();

        assertEquals(0, plan.getTotalPlanned().compareTo(new BigDecimal("30")));
    }

    @Test
    @DisplayName("空分录拒绝建单")
    void shouldRejectEmptyLines() {
        MpsPlan plan = newPlan();
        plan.setLines(new ArrayList<>());

        BizException exception = assertThrows(BizException.class, plan::calculateTotal);

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("下达：状态为已下达且分录回写已下达数量")
    void shouldRelease() {
        MpsPlan plan = newPlan();
        plan.setStatus(MfgStatus.DRAFT);
        plan.calculateTotal();

        plan.release();

        assertEquals(MfgStatus.RELEASED, plan.getStatus());
        assertEquals(0, plan.getLines().get(0).getReleasedQuantity().compareTo(new BigDecimal("10")));
    }

    @Test
    @DisplayName("重复下达拒绝")
    void shouldRejectReleaseTwice() {
        MpsPlan plan = newPlan();
        plan.setStatus(MfgStatus.RELEASED);

        BizException exception = assertThrows(BizException.class, plan::release);

        assertEquals(ErpErrorCode.MFG_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已下达计划不可取消")
    void shouldRejectCancelAfterRelease() {
        MpsPlan plan = newPlan();
        plan.setStatus(MfgStatus.RELEASED);

        BizException exception = assertThrows(BizException.class, plan::cancel);

        assertEquals(ErpErrorCode.MFG_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造主生产计划。
     *
     * @return 计划
     */
    private MpsPlan newPlan() {
        MpsPlan plan = new MpsPlan();
        MpsPlanLine first = new MpsPlanLine();
        first.setLineNo(1);
        first.setItemCode("ERP-001");
        first.setPlannedQuantity(new BigDecimal("10"));
        first.setReleasedQuantity(BigDecimal.ZERO);
        MpsPlanLine second = new MpsPlanLine();
        second.setLineNo(2);
        second.setItemCode("ERP-002");
        second.setPlannedQuantity(new BigDecimal("20"));
        second.setReleasedQuantity(BigDecimal.ZERO);
        plan.setLines(List.of(first, second));
        return plan;
    }
}
