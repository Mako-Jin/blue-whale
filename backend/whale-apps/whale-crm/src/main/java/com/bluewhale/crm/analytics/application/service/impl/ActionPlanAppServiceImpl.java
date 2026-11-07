package com.bluewhale.crm.analytics.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.analytics.application.service.ActionPlanAppService;
import com.bluewhale.crm.analytics.domain.model.ActionCandidate;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemQuery;
import com.bluewhale.crm.analytics.domain.model.ActionItemStatus;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.analytics.domain.model.ActionRuleType;
import com.bluewhale.crm.analytics.domain.port.AnalyticsSourcePort;
import com.bluewhale.crm.analytics.domain.repository.ActionItemRepository;
import com.bluewhale.crm.analytics.domain.repository.ActionRuleRepository;
import com.bluewhale.crm.analytics.domain.service.ActionRuleEngine;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import com.bluewhale.crm.config.CrmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 行动计划应用服务实现。
 *
 * <p>规则缺失时按内置类型自动补建；条件解除的未关闭行动项自动关闭；
 * 已关闭行动项的去重键仍保留，避免重复打扰。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ActionPlanAppServiceImpl implements ActionPlanAppService {

    /** 默认租户 */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 月份格式 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 自动关闭原因 */
    private static final String AUTO_CLOSE_REASON = "条件已解除，系统自动关闭";

    private final ActionRuleRepository actionRuleRepository;
    private final ActionItemRepository actionItemRepository;
    private final AnalyticsSourcePort analyticsSourcePort;
    private final CrmProperties crmProperties;
    private final CrmAnalyticsProperties analyticsProperties;

    /** 规则引擎（纯 Java） */
    private final ActionRuleEngine actionRuleEngine = new ActionRuleEngine();

    /**
     * 分页查询行动项。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    @Override
    public PageResult<ActionItem> page(ActionItemQuery query, CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            query.setScopeOwnerId(actor.requireUserId());
        }
        List<ActionItem> records = actionItemRepository.page(query);
        long total = actionItemRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    /**
     * 按规则生成行动项。
     *
     * @param actor 操作人
     * @return 新增条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generate(CrmActor actor) {
        assertGate();
        LocalDate statDate = LocalDate.now();
        List<ActionRule> rules = ensureRules();
        int generated = 0;
        for (ActionRule rule : rules) {
            if (rule == null || !Boolean.TRUE.equals(rule.getEnabled()) || rule.getRuleType() == null) {
                continue;
            }
            List<ActionCandidate> candidates = candidatesFor(rule, statDate);
            List<ActionItem> existing = actionItemRepository.findByRuleCode(rule.getRuleCode());
            Set<String> existingKeys = new HashSet<>();
            for (ActionItem item : existing) {
                if (item.getDedupKey() != null) {
                    existingKeys.add(item.getDedupKey());
                }
            }
            List<ActionItem> newItems = actionRuleEngine.generate(rule, candidates, existingKeys, statDate);
            for (ActionItem item : newItems) {
                item.setTenantId(DEFAULT_TENANT_ID);
                actionItemRepository.insert(item);
                generated++;
            }
            autoCloseResolved(rule, candidates, existing, statDate, actor);
        }
        return generated;
    }

    /**
     * 关闭规则。
     *
     * @param id 规则主键
     * @param actor 操作人
     * @return 更新后的规则
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActionRule closeRule(Long id, CrmActor actor) {
        assertGate();
        ActionRule rule = actionRuleRepository.findById(id);
        if (rule == null) {
            throw CrmErrorCode.exception(CrmErrorCode.ACTION_RULE_NOT_FOUND);
        }
        rule.disable();
        actionRuleRepository.update(rule);
        return rule;
    }

    /**
     * 手工关闭行动项。
     *
     * @param id 行动项主键
     * @param reason 关闭原因
     * @param actor 操作人
     * @return 更新后的行动项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActionItem closeItem(Long id, String reason, CrmActor actor) {
        assertGate();
        ActionItem item = actionItemRepository.findById(id);
        if (item == null) {
            throw CrmErrorCode.exception(CrmErrorCode.ACTION_ITEM_NOT_FOUND);
        }
        if (!actor.allDataScope() && item.getOwnerId() != null
                && !item.getOwnerId().equals(actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.ACTION_ITEM_NOT_FOUND);
        }
        Long operatorId = actor.getUserId();
        item.close(reason, operatorId, LocalDateTime.now());
        actionItemRepository.update(item);
        return item;
    }

    /**
     * 查询全部行动规则。
     *
     * @param actor 操作人
     * @return 规则列表
     */
    @Override
    public List<ActionRule> listRules(CrmActor actor) {
        assertGate();
        return actionRuleRepository.findAll();
    }

    /**
     * 条件解除的未关闭行动项自动关闭。
     *
     * @param rule 规则
     * @param candidates 当前候选
     * @param existing 既有行动项
     * @param statDate 统计日
     * @param actor 操作人
     */
    private void autoCloseResolved(ActionRule rule, List<ActionCandidate> candidates,
                                   List<ActionItem> existing, LocalDate statDate, CrmActor actor) {
        Set<String> candidateKeys = new HashSet<>();
        for (ActionCandidate candidate : candidates) {
            String key = ActionRuleEngine.dedupKey(rule.getRuleType(), candidate, statDate);
            if (key != null) {
                candidateKeys.add(key);
            }
        }
        for (ActionItem item : existing) {
            if (item.getStatus() == ActionItemStatus.OPEN && item.getDedupKey() != null
                    && !candidateKeys.contains(item.getDedupKey())) {
                item.close(AUTO_CLOSE_REASON, actor.getUserId(), LocalDateTime.now());
                actionItemRepository.update(item);
            }
        }
    }

    /**
     * 按规则加载候选数据。
     *
     * @param rule 规则
     * @param statDate 统计日
     * @return 候选数据
     */
    private List<ActionCandidate> candidatesFor(ActionRule rule, LocalDate statDate) {
        int threshold = rule.getThresholdDays() == null ? rule.getRuleType().getDefaultThresholdDays()
                : rule.getThresholdDays();
        return switch (rule.getRuleType()) {
            case CUSTOMER_IDLE -> analyticsSourcePort.idleCustomers(
                    threshold > 0 ? threshold : analyticsProperties.getKeyCustomerIdleDays(),
                    statDate, null, null);
            case OPPORTUNITY_OVERDUE -> analyticsSourcePort.overdueOpportunities(
                    threshold > 0 ? threshold : analyticsProperties.getOpportunityOverdueDays(),
                    statDate, null, null);
            case TARGET_GAP -> analyticsSourcePort.targetGaps(
                    YearMonth.from(statDate).format(MONTH_FORMAT), null, null);
            case CONTRACT_EXPIRE -> analyticsSourcePort.expiringContracts(
                    threshold > 0 ? threshold : analyticsProperties.getContractExpireDays(),
                    statDate, null, null);
            case AR_OVERDUE -> analyticsSourcePort.overdueReceivables(
                    new BigDecimal(analyticsProperties.getOverdueAmountThreshold()), statDate, null, null);
            case CSAT_LOW -> analyticsSourcePort.lowCsatTickets(
                    2, statDate.minusDays(30), statDate, null, null);
        };
    }

    /**
     * 确保六类规则存在，缺失时按默认配置补建。
     *
     * @return 全部规则
     */
    private List<ActionRule> ensureRules() {
        List<ActionRule> rules = new ArrayList<>(actionRuleRepository.findAll());
        Set<ActionRuleType> existingTypes = new HashSet<>();
        for (ActionRule rule : rules) {
            if (rule.getRuleType() != null) {
                existingTypes.add(rule.getRuleType());
            }
        }
        for (ActionRuleType type : ActionRuleType.values()) {
            if (existingTypes.contains(type)) {
                continue;
            }
            ActionRule rule = defaultRule(type);
            actionRuleRepository.insert(rule);
            rules.add(rule);
        }
        return rules;
    }

    /**
     * 构建默认规则。
     *
     * @param type 规则类型
     * @return 规则
     */
    private ActionRule defaultRule(ActionRuleType type) {
        ActionRule rule = new ActionRule();
        rule.setTenantId(DEFAULT_TENANT_ID);
        rule.setRuleCode(type.name());
        rule.setRuleName(type.getLabel());
        rule.setRuleType(type);
        rule.setEnabled(Boolean.TRUE);
        rule.setDescription("系统内置规则：" + type.getLabel());
        rule.setThresholdDays(defaultThreshold(type));
        return rule;
    }

    /**
     * 解析默认阈值。
     *
     * @param type 规则类型
     * @return 阈值天数
     */
    private int defaultThreshold(ActionRuleType type) {
        return switch (type) {
            case CUSTOMER_IDLE -> analyticsProperties.getKeyCustomerIdleDays();
            case OPPORTUNITY_OVERDUE -> analyticsProperties.getOpportunityOverdueDays();
            case CONTRACT_EXPIRE -> analyticsProperties.getContractExpireDays();
            default -> type.getDefaultThresholdDays();
        };
    }

    /**
     * 门禁校验。
     */
    private void assertGate() {
        if (!crmProperties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
        if (!analyticsProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED, "数据分析模块未启用");
        }
    }

}
