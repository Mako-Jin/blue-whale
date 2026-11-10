package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    @DisplayName("工序报工：非首道累计合格不得超过前道")
    void shouldRejectOperationQualifiedExceeded() {
        ProductionOrder order = orderWithOperations();
        order.setStatus(MfgStatus.RELEASED);
        order.reportOperation(10, new BigDecimal("5"), BigDecimal.ZERO, LocalDateTime.now());

        BizException exception = assertThrows(BizException.class,
                () -> order.reportOperation(20, new BigDecimal("6"), BigDecimal.ZERO, LocalDateTime.now()));

        assertEquals(ErpErrorCode.OPERATION_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("工序报工：合法累计并进入生产中")
    void shouldReportOperation() {
        ProductionOrder order = orderWithOperations();
        order.setStatus(MfgStatus.RELEASED);
        order.reportOperation(10, new BigDecimal("4"), new BigDecimal("1"), LocalDateTime.now());
        order.reportOperation(20, new BigDecimal("4"), BigDecimal.ZERO, LocalDateTime.now());

        assertEquals(MfgStatus.IN_PROGRESS, order.getStatus());
        assertEquals(0, order.getOperations().get(0).getQualifiedQuantity().compareTo(new BigDecimal("4")));
        assertEquals(0, order.getOperations().get(1).getQualifiedQuantity().compareTo(new BigDecimal("4")));
    }

    @Test
    @DisplayName("完工数量受末道工序累计合格限制")
    void shouldRejectCompleteExceededOperationQualified() {
        ProductionOrder order = issuedOrder();
        order.setOperations(List.of(operation(10, new BigDecimal("4"))));

        BizException exception = assertThrows(BizException.class,
                () -> order.complete(new BigDecimal("5"), LocalDate.of(2026, 11, 4)));

        assertEquals(ErpErrorCode.OPERATION_INVALID, exception.getCode());
        assertEquals(0, order.complete(new BigDecimal("4"), LocalDate.of(2026, 11, 4))
                .compareTo(new BigDecimal("40")));
    }

    @Test
    @DisplayName("退料按已领成本比例冲减，全退结清尾差")
    void shouldReturnMaterialByIssuedCost() {
        ProductionOrder order = issuedOrder();
        BigDecimal first = order.returnMaterial(order.getMaterials().get(0), new BigDecimal("3"));
        BigDecimal rest = order.returnMaterial(order.getMaterials().get(0), new BigDecimal("7"));

        assertEquals(0, first.compareTo(new BigDecimal("30")));
        assertEquals(0, rest.compareTo(new BigDecimal("70")));
        assertEquals(0, order.getTotalMaterialCost().compareTo(BigDecimal.ZERO));
        assertEquals(0, order.getMaterials().get(0).getIssuedQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, order.getMaterials().get(0).getIssuedCost().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("报废数量超过已领拒绝")
    void shouldRejectScrapExceeded() {
        ProductionOrder order = issuedOrder();

        BizException exception = assertThrows(BizException.class,
                () -> order.scrapMaterial(order.getMaterials().get(0), new BigDecimal("11")));

        assertEquals(ErpErrorCode.MATERIAL_SCRAP_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("倒冲目标按完工比例计算，最后一批取整单需求")
    void shouldCalculateBackflushTarget() {
        ProductionOrder order = newOrder();
        ProductionMaterial material = order.getMaterials().get(0);

        assertEquals(0, material.backflushTarget(BigDecimal.ZERO, new BigDecimal("5"),
                new BigDecimal("10")).compareTo(new BigDecimal("5")));
        assertEquals(0, material.backflushTarget(new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("10")).compareTo(new BigDecimal("10")));
    }

    /**
     * 构造含两道工序的已领料订单。
     *
     * @return 生产订单
     */
    private ProductionOrder orderWithOperations() {
        ProductionOrder order = issuedOrder();
        order.setOperations(List.of(operation(10, BigDecimal.ZERO), operation(20, BigDecimal.ZERO)));
        return order;
    }

    /**
     * 构造工序。
     *
     * @param stepNo  工序号
     * @param qualified 累计合格数量
     * @return 工序
     */
    private ProductionOperation operation(int stepNo, BigDecimal qualified) {
        ProductionOperation operation = new ProductionOperation();
        operation.setStepNo(stepNo);
        operation.setName("工序" + stepNo);
        operation.setWorkCenterCode("WC-01");
        operation.setPlannedQuantity(new BigDecimal("10"));
        operation.setQualifiedQuantity(qualified);
        operation.setScrappedQuantity(BigDecimal.ZERO);
        operation.setStatus(MfgStatus.PENDING);
        return operation;
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
        order.getMaterials().get(0).accumulateIssuedCost(new BigDecimal("100"));
        order.accumulateMaterialCost(new BigDecimal("100"));
        return order;
    }
}
