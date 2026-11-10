package com.bluewhale.crm.analytics;

import com.bluewhale.crm.analytics.domain.model.ActionCandidate;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemStatus;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.analytics.domain.model.ActionRuleType;
import com.bluewhale.crm.analytics.domain.model.ActionType;
import com.bluewhale.crm.analytics.domain.service.ActionRuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ActionRuleEngine} 规则生成与去重口径单元测试。
 *
 * @author BlueWhale
 */
class ActionRuleEngineTest {

    /** 统计日 */
    private static final LocalDate STAT_DATE = LocalDate.of(2026, 11, 8);

    /** 被测规则引擎 */
    private final ActionRuleEngine engine = new ActionRuleEngine();

    @Test
    @DisplayName("客户未跟进：生成 OPEN 行动项并写入月度去重键")
    void shouldGenerateIdleCustomerItem() {
        List<ActionItem> items = engine.generate(rule(ActionRuleType.CUSTOMER_IDLE),
                List.of(candidate(100L, 20L)), Set.of(), STAT_DATE);

        assertEquals(1, items.size());
        ActionItem item = items.get(0);
        assertEquals("CUSTOMER_IDLE", item.getRuleCode());
        assertEquals("KEY_CUSTOMER_IDLE:100:2026-11", item.getDedupKey());
        assertEquals(ActionItemStatus.OPEN, item.getStatus());
        assertEquals(ActionType.CUSTOMER, item.getRefType());
        assertEquals(100L, item.getCustomerId());
        assertEquals("客户A", item.getCustomerName());
        assertEquals(20L, item.getOwnerId());
        assertEquals(30L, item.getDeptId());
        assertEquals("P2", item.getPriority());
        assertEquals(STAT_DATE.plusDays(7), item.getDueDate());
        assertTrue(item.getTitle().contains("待跟进"));
    }

    @Test
    @DisplayName("去重：已存在同一去重键的候选不再生成")
    void shouldSkipExistingDedupKey() {
        List<ActionItem> items = engine.generate(rule(ActionRuleType.CUSTOMER_IDLE),
                List.of(candidate(100L, 20L)), Set.of("KEY_CUSTOMER_IDLE:100:2026-11"), STAT_DATE);

        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("关闭后不再生成：已关闭行动项的去重键仍参与去重，新键正常生成")
    void shouldNotRegenerateClosedItem() {
        List<ActionItem> items = engine.generate(rule(ActionRuleType.CUSTOMER_IDLE),
                List.of(candidate(100L, 20L), candidate(200L, 21L)),
                Set.of("KEY_CUSTOMER_IDLE:100:2026-11"), STAT_DATE);

        assertEquals(1, items.size());
        assertEquals("KEY_CUSTOMER_IDLE:200:2026-11", items.get(0).getDedupKey());
    }

    @Test
    @DisplayName("应收逾期：按客户与月份生成去重键，并给出 3 天内完成建议")
    void shouldGenerateArOverdueItem() {
        ActionCandidate candidate = candidate(100L, 20L);
        candidate.setActionType(ActionType.INVOICE);
        candidate.setMetricValue(new BigDecimal("1200"));

        List<ActionItem> items = engine.generate(rule(ActionRuleType.AR_OVERDUE),
                List.of(candidate), Set.of(), STAT_DATE);

        assertEquals(1, items.size());
        ActionItem item = items.get(0);
        assertEquals("AR_OVERDUE:100:2026-11", item.getDedupKey());
        assertTrue(item.getTitle().contains("逾期应收"));
        assertTrue(item.getSuggestion().contains("1200"));
        assertEquals(STAT_DATE.plusDays(3), item.getDueDate());
    }

    @Test
    @DisplayName("候选为空或规则类型缺失时不生成行动项")
    void shouldReturnEmptyWhenNoCandidateOrRuleType() {
        assertTrue(engine.generate(rule(ActionRuleType.CUSTOMER_IDLE), List.of(), Set.of(), STAT_DATE).isEmpty());

        ActionRule rule = new ActionRule();
        rule.setRuleCode("UNKNOWN");
        assertTrue(engine.generate(rule, List.of(candidate(100L, 20L)), Set.of(), STAT_DATE).isEmpty());
    }

    /**
     * 构造启用规则。
     *
     * @param type 规则类型
     * @return 规则
     */
    private ActionRule rule(ActionRuleType type) {
        ActionRule rule = new ActionRule();
        rule.setRuleCode(type.name());
        rule.setRuleName(type.getLabel());
        rule.setRuleType(type);
        rule.setEnabled(Boolean.TRUE);
        rule.setThresholdDays(type.getDefaultThresholdDays());
        return rule;
    }

    /**
     * 构造候选数据。
     *
     * @param customerId 客户 ID
     * @param ownerId 负责人
     * @return 候选数据
     */
    private ActionCandidate candidate(Long customerId, Long ownerId) {
        ActionCandidate candidate = new ActionCandidate();
        candidate.setActionType(ActionType.CUSTOMER);
        candidate.setRefId(customerId);
        candidate.setCustomerId(customerId);
        candidate.setCustomerName("客户A");
        candidate.setTitle("客户A");
        candidate.setOwnerId(ownerId);
        candidate.setDeptId(30L);
        return candidate;
    }
}
