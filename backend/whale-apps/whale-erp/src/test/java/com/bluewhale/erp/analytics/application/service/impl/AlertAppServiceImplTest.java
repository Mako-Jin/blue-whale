package com.bluewhale.erp.analytics.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.analytics.application.command.AlertHandleCommand;
import com.bluewhale.erp.analytics.application.command.AlertRuleSaveCommand;
import com.bluewhale.erp.analytics.application.view.AlertEvaluationView;
import com.bluewhale.erp.analytics.application.view.AlertEventView;
import com.bluewhale.erp.analytics.domain.model.AlertEvent;
import com.bluewhale.erp.analytics.domain.model.AlertRule;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.model.ReceivableFact;
import com.bluewhale.erp.analytics.domain.model.StockFact;
import com.bluewhale.erp.analytics.domain.repository.AlertRepository;
import com.bluewhale.erp.analytics.domain.repository.AnalyticsRepository;
import com.bluewhale.erp.common.ErpErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class AlertAppServiceImplTest {

    private final AlertRepository alertRepository = mock(AlertRepository.class);

    private final AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);

    private final AlertAppServiceImpl service =
            new AlertAppServiceImpl(alertRepository, analyticsRepository);

    @Test
    @DisplayName("库存不足触发预警事件")
    void shouldTriggerStockLowEvent() {
        AlertRule rule = rule("R001", AnalyticsStatus.RULE_STOCK_LOW, new BigDecimal("5"));
        when(alertRepository.listEnabledRules()).thenReturn(List.of(rule));
        when(analyticsRepository.listStockFacts()).thenReturn(List.of(
                stock("I001", 1L, new BigDecimal("3")), stock("I002", 1L, new BigDecimal("9"))));
        when(alertRepository.existsEvent(any(), any(), any())).thenReturn(false);
        when(alertRepository.nextEventNo(any())).thenReturn("AE-20261104-0001");

        AlertEvaluationView view = service.evaluate(null, "tester");

        assertEquals(1, view.getCreatedEvents());
        assertEquals(0, view.getDuplicateSkipped());
        verify(alertRepository).insertEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("同规则同对象同周期重复评估不重复触发")
    void shouldSkipDuplicateEvent() {
        AlertRule rule = rule("R001", AnalyticsStatus.RULE_STOCK_LOW, new BigDecimal("5"));
        when(alertRepository.listEnabledRules()).thenReturn(List.of(rule));
        when(analyticsRepository.listStockFacts())
                .thenReturn(List.of(stock("I001", 1L, new BigDecimal("3"))));
        when(alertRepository.existsEvent(rule.getId(), "I001#1", "20261104")).thenReturn(true);

        AlertEvaluationView view = service.evaluate(null, "tester");

        assertEquals(0, view.getCreatedEvents());
        assertEquals(1, view.getDuplicateSkipped());
        verify(alertRepository, never()).insertEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("超期应收按超期天数触发")
    void shouldTriggerOverdueReceivable() {
        AlertRule rule = rule("R002", AnalyticsStatus.RULE_RECEIVABLE_OVERDUE, new BigDecimal("30"));
        when(alertRepository.listEnabledRules()).thenReturn(List.of(rule));
        when(analyticsRepository.listReceivableFacts()).thenReturn(List.of(receivable(60)));
        when(alertRepository.nextEventNo(any())).thenReturn("AE-20261104-0001");

        AlertEvaluationView view = service.evaluate(null, "tester");

        assertEquals(1, view.getCreatedEvents());
        verify(alertRepository).insertEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("预算预警因数据源缺失跳过并给出说明")
    void shouldSkipBudgetRuleWithoutSource() {
        AlertRule rule = rule("R003", AnalyticsStatus.RULE_BUDGET_EXCEEDED, BigDecimal.ZERO);
        when(alertRepository.listEnabledRules()).thenReturn(List.of(rule));

        AlertEvaluationView view = service.evaluate(null, "tester");

        assertEquals(0, view.getCreatedEvents());
        assertTrue(view.getSkippedRules().contains("R003"));
        assertTrue(view.getNote().contains("BUDGET_EXCEEDED"));
    }

    @Test
    @DisplayName("确认预警事件后状态推进")
    void shouldAcknowledgeEvent() {
        AlertEvent event = event(AnalyticsStatus.EVENT_OPEN);
        when(alertRepository.findEventById(1L)).thenReturn(Optional.of(event));

        AlertHandleCommand command = new AlertHandleCommand();
        command.setOperator("alice");
        AlertEventView view = service.acknowledge(1L, command);

        assertEquals(AnalyticsStatus.EVENT_ACKNOWLEDGED, view.getStatus());
        assertEquals("alice", view.getAcknowledgedBy());
        verify(alertRepository).updateEvent(event);
    }

    @Test
    @DisplayName("关闭预警事件后不可重复确认")
    void shouldRejectAcknowledgeClosedEvent() {
        AlertEvent event = event(AnalyticsStatus.EVENT_CLOSED);
        when(alertRepository.findEventById(1L)).thenReturn(Optional.of(event));

        BizException exception = assertThrows(BizException.class,
                () -> service.acknowledge(1L, new AlertHandleCommand()));

        assertEquals(ErpErrorCode.ALERT_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("规则编号重复拒绝新建")
    void shouldRejectDuplicateRule() {
        when(alertRepository.findRuleByNo("R001")).thenReturn(Optional.of(
                rule("R001", AnalyticsStatus.RULE_STOCK_LOW, BigDecimal.ZERO)));

        AlertRuleSaveCommand command = new AlertRuleSaveCommand();
        command.setRuleNo("R001");
        command.setRuleName("库存不足");
        command.setRuleType(AnalyticsStatus.RULE_STOCK_LOW);

        BizException exception = assertThrows(BizException.class, () -> service.createRule(command));

        assertEquals(ErpErrorCode.ALERT_RULE_NO_CONFLICT, exception.getCode());
        verify(alertRepository, never()).insertRule(any(AlertRule.class));
    }

    /**
     * 构造规则。
     *
     * @param ruleNo 编号
     * @param type 类型
     * @param threshold 阈值
     * @return 规则
     */
    private AlertRule rule(String ruleNo, String type, BigDecimal threshold) {
        AlertRule rule = new AlertRule();
        rule.setId(9L);
        rule.setRuleNo(ruleNo);
        rule.setRuleName("规则" + ruleNo);
        rule.setRuleType(type);
        rule.setThresholdValue(threshold);
        rule.setPeriodType(AnalyticsStatus.PERIOD_DAY);
        rule.setLevel(AnalyticsStatus.LEVEL_WARN);
        rule.setStatus(AnalyticsStatus.ENABLED);
        return rule;
    }

    /**
     * 构造库存事实。
     *
     * @param itemCode 物料编码
     * @param warehouseId 仓库
     * @param quantity 数量
     * @return 库存事实
     */
    private StockFact stock(String itemCode, Long warehouseId, BigDecimal quantity) {
        StockFact fact = new StockFact();
        fact.setItemCode(itemCode);
        fact.setItemName(itemCode);
        fact.setWarehouseId(warehouseId);
        fact.setQuantity(quantity);
        return fact;
    }

    /**
     * 构造超期应收事实。
     *
     * @param overdueDays 超期天数
     * @return 应收事实
     */
    private ReceivableFact receivable(int overdueDays) {
        ReceivableFact fact = new ReceivableFact();
        fact.setDocNo("AR-001");
        fact.setDueDate(LocalDate.now().minusDays(overdueDays));
        fact.setAmount(new BigDecimal("100"));
        fact.setSettledAmount(BigDecimal.ZERO);
        fact.setBadDebtAmount(BigDecimal.ZERO);
        fact.setStatus("OPEN");
        return fact;
    }

    /**
     * 构造事件。
     *
     * @param status 状态
     * @return 事件
     */
    private AlertEvent event(String status) {
        AlertEvent event = new AlertEvent();
        event.setId(1L);
        event.setEventNo("AE-20261104-0001");
        event.setRuleId(9L);
        event.setRuleNo("R001");
        event.setRuleType(AnalyticsStatus.RULE_STOCK_LOW);
        event.setObjectKey("I001#1");
        event.setPeriodCode("20261104");
        event.setMetricValue(new BigDecimal("3"));
        event.setThresholdValue(new BigDecimal("5"));
        event.setLevel(AnalyticsStatus.LEVEL_WARN);
        event.setStatus(status);
        event.setTriggeredAt(LocalDateTime.now());
        return event;
    }
}
