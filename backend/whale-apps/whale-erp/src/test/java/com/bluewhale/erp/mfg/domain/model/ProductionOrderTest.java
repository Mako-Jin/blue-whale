package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ProductionOrder} 单元测试。
 *
 * @author BlueWhale
 */
class ProductionOrderTest {

    @Test
    @DisplayName("用料为空拒绝建单")
    void shouldRejectEmptyMaterials() {
        ProductionOrder order = newOrder();
        order.setMaterials(new ArrayList<>());

        BizException exception = assertThrows(BizException.class, order::validateForCreate);

        assertEquals(ErpErrorCode.QUANTITY_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("计划完工日期早于开工日期拒绝建单")
    void shouldRejectDateReversed() {
        ProductionOrder order = newOrder();
        order.setPlanEndDate(order.getPlanStartDate().minusDays(1));

        BizException exception = assertThrows(BizException.class, order::validateForCreate);

        assertEquals(ErpErrorCode.DOC_DATE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("领料累加已领数量并进入生产中")
    void shouldIssueMaterial() {
        ProductionOrder order = newOrder();
        order.setStatus(MfgStatus.RELEASED);

        order.issue(order.getMaterials().get(0), new BigDecimal("4"));

        assertEquals(MfgStatus.IN_PROGRESS, order.getStatus());
        assertEquals(0, order.getMaterials().get(0).getIssuedQuantity().compareTo(new BigDecimal("4")));
    }

    @Test
    @DisplayName("超领拒绝")
    void shouldRejectOverIssue() {
        ProductionOrder order = newOrder();
        order.setStatus(MfgStatus.RELEASED);

        BizException exception = assertThrows(BizException.class,
                () -> order.issue(order.getMaterials().get(0), new BigDecimal("11")));

        assertEquals(ErpErrorCode.MATERIAL_ISSUE_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("未领完用料时拒绝完工")
    void shouldRejectCompleteBeforeIssued() {
        ProductionOrder order = newOrder();
        order.setStatus(MfgStatus.IN_PROGRESS);

        BizException exception = assertThrows(BizException.class,
                () -> order.complete(new BigDecimal("1"), LocalDate.of(2026, 11, 4)));

        assertEquals(ErpErrorCode.MATERIAL_NOT_ISSUED, exception.getCode());
    }

    @Test
    @DisplayName("部分完工：按领料成本结转本次入库金额")
    void shouldCompletePartially() {
        ProductionOrder order = issuedOrder();

        BigDecimal amount = order.complete(new BigDecimal("5"), LocalDate.of(2026, 11, 4));

        assertEquals(MfgStatus.IN_PROGRESS, order.getStatus());
        assertEquals(0, order.getCompletedQuantity().compareTo(new BigDecimal("5")));
        assertEquals(0, amount.compareTo(new BigDecimal("50")));
    }

    @Test
    @DisplayName("全部完工：最后一批结清剩余成本并置为已完工")
    void shouldCompleteFully() {
        ProductionOrder order = issuedOrder();
        order.complete(new BigDecimal("5"), LocalDate.of(2026, 11, 4));

        BigDecimal amount = order.complete(new BigDecimal("5"), LocalDate.of(2026, 11, 5));

        assertEquals(MfgStatus.COMPLETED, order.getStatus());
        assertEquals(0, amount.compareTo(new BigDecimal("50")));
        assertEquals(0, order.getCompletedCost().compareTo(new BigDecimal("100")));
    }

    @Test
    @DisplayName("完工数量超过计划拒绝")
    void shouldRejectCompleteExceeded() {
        ProductionOrder order = issuedOrder();

        BizException exception = assertThrows(BizException.class,
                () -> order.complete(new BigDecimal("11"), LocalDate.of(2026, 11, 4)));

        assertEquals(ErpErrorCode.PRODUCTION_COMPLETE_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("只有已完工订单可结案")
    void shouldRejectCloseBeforeCompleted() {
        ProductionOrder order = issuedOrder();

        BizException exception = assertThrows(BizException.class, order::close);

        assertEquals(ErpErrorCode.MFG_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("已领料订单不可取消")
    void shouldRejectCancelAfterIssue() {
        ProductionOrder order = issuedOrder();

        BizException exception = assertThrows(BizException.class, order::cancel);

        assertEquals(ErpErrorCode.MFG_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造生产订单（草稿 + 一条用料）。
     *
     * @return 生产订单
     */
    private ProductionOrder newOrder() {
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo("MO-20261104-0001");
        order.setItemId(3L);
        order.setItemCode("ERP-001");
        order.setItemName("成品A");
        order.setPlannedQuantity(new BigDecimal("10"));
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setCompletedCost(BigDecimal.ZERO);
        order.setTotalMaterialCost(BigDecimal.ZERO);
        order.setIssueWarehouseId(7301L);
        order.setIssueWarehouseName("主仓");
        order.setTargetWarehouseId(7302L);
        order.setTargetWarehouseName("成品仓");
        order.setPlanStartDate(LocalDate.of(2026, 11, 4));
        order.setStatus(MfgStatus.DRAFT);
        ProductionMaterial material = new ProductionMaterial();
        material.setLineNo(1);
        material.setItemId(4L);
        material.setItemCode("ERP-002");
        material.setItemName("材料B");
        material.setRequiredQuantity(new BigDecimal("10"));
        material.setIssuedQuantity(BigDecimal.ZERO);
        order.setMaterials(List.of(material));
        return order;
    }

    /**
     * 构造已领料 100 成本、生产中订单。
     *
     * @return 生产订单
     */
    private ProductionOrder issuedOrder() {
        ProductionOrder order = newOrder();
        order.setStatus(MfgStatus.RELEASED);
        order.issue(order.getMaterials().get(0), new BigDecimal("10"));
        order.accumulateMaterialCost(new BigDecimal("100"));
        return order;
    }
}
