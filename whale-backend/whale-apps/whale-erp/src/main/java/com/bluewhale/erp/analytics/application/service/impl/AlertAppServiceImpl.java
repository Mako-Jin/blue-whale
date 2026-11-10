package com.bluewhale.erp.analytics.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.analytics.application.command.AlertHandleCommand;
import com.bluewhale.erp.analytics.application.command.AlertRuleSaveCommand;
import com.bluewhale.erp.analytics.application.service.AlertAppService;
import com.bluewhale.erp.analytics.application.view.AlertEvaluationView;
import com.bluewhale.erp.analytics.application.view.AlertEventView;
import com.bluewhale.erp.analytics.application.view.AlertRuleView;
import com.bluewhale.erp.analytics.domain.model.AlertEvent;
import com.bluewhale.erp.analytics.domain.model.AlertRule;
import com.bluewhale.erp.analytics.domain.model.AnalyticsQuery;
import com.bluewhale.erp.analytics.domain.model.AnalyticsStatus;
import com.bluewhale.erp.analytics.domain.model.QualityFact;
import com.bluewhale.erp.analytics.domain.model.ReceivableFact;
import com.bluewhale.erp.analytics.domain.model.StockFact;
import com.bluewhale.erp.analytics.domain.repository.AlertRepository;
import com.bluewhale.erp.analytics.domain.repository.AnalyticsRepository;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * 预警中心应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AlertAppServiceImpl implements AlertAppService {

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "system";

    private final AlertRepository alertRepository;

    private final AnalyticsRepository analyticsRepository;

    @Override
    public PageResult<AlertRuleView> pageRules(AnalyticsQuery query) {
        long total = alertRepository.countRules(query);
        List<AlertRuleView> records = alertRepository.pageRules(query).stream().map(this::toRuleView).toList();
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional
    public AlertRuleView createRule(AlertRuleSaveCommand command) {
        if (alertRepository.findRuleByNo(command.getRuleNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.ALERT_RULE_NO_CONFLICT, "预警规则编号已存在");
        }
        AlertRule rule = toRule(command);
        rule.validateForCreate();
        alertRepository.insertRule(rule);
        return toRuleView(rule);
    }

    @Override
    @Transactional
    public AlertRuleView updateRule(String ruleNo, AlertRuleSaveCommand command) {
        AlertRule rule = alertRepository.findRuleByNo(ruleNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ALERT_RULE_NOT_FOUND));
        rule.setRuleName(command.getRuleName());
        rule.setRuleType(command.getRuleType());
        rule.setThresholdValue(command.getThresholdValue());
        rule.setPeriodType(command.getPeriodType());
        rule.setLevel(command.getLevel());
        rule.setSuggestion(command.getSuggestion());
        rule.setStatus(command.getStatus());
        rule.setRemark(command.getRemark());
        rule.validateForCreate();
        alertRepository.updateRule(rule);
        return toRuleView(rule);
    }

    @Override
    public PageResult<AlertEventView> pageEvents(AnalyticsQuery query) {
        long total = alertRepository.countEvents(query);
        List<AlertEventView> records = alertRepository.pageEvents(query).stream()
                .map(this::toEventView).toList();
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional
    public AlertEvaluationView evaluate(String ruleNo, String operator) {
        List<AlertRule> rules;
        if (StringUtils.hasText(ruleNo)) {
            rules = List.of(alertRepository.findRuleByNo(ruleNo)
                    .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ALERT_RULE_NOT_FOUND)));
        } else {
            rules = alertRepository.listEnabledRules();
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String triggerOperator = StringUtils.hasText(operator) ? operator : DEFAULT_OPERATOR;
        int created = 0;
        int duplicated = 0;
        List<String> skipped = new ArrayList<>();
        for (AlertRule rule : rules) {
            if (!AnalyticsStatus.ENABLED.equals(rule.getStatus())) {
                continue;
            }
            List<Candidate> candidates = candidates(rule, today);
            if (candidates.isEmpty() && AnalyticsStatus.RULE_BUDGET_EXCEEDED.equals(rule.getRuleType())) {
                skipped.add(rule.getRuleNo());
                continue;
            }
            for (Candidate candidate : candidates) {
                String period = periodCode(rule.getPeriodType(), today);
                if (alertRepository.existsEvent(rule.getId(), candidate.objectKey, period)) {
                    duplicated++;
                    continue;
                }
                AlertEvent event = new AlertEvent();
                event.setEventNo(alertRepository.nextEventNo(today));
                event.setRuleId(rule.getId());
                event.setRuleNo(rule.getRuleNo());
                event.setRuleType(rule.getRuleType());
                event.setObjectKey(candidate.objectKey);
                event.setPeriodCode(period);
                event.setMetricValue(candidate.metric);
                event.setThresholdValue(rule.getThresholdValue());
                event.setLevel(rule.getLevel());
                event.setSuggestion(rule.getSuggestion());
                event.setTriggeredAt(now);
                event.validateForCreate();
                alertRepository.insertEvent(event);
                created++;
            }
        }
        AlertEvaluationView view = new AlertEvaluationView();
        view.setEvaluatedRules(rules.size());
        view.setCreatedEvents(created);
        view.setDuplicateSkipped(duplicated);
        view.setSkippedRules(skipped);
        view.setNote(skipped.isEmpty() ? "评估完成，操作人 " + triggerOperator
                : "BUDGET_EXCEEDED 依赖预算模块数据（M2 财务扩展未落地），已跳过");
        return view;
    }

    @Override
    @Transactional
    public AlertEventView acknowledge(Long id, AlertHandleCommand command) {
        AlertEvent event = alertRepository.findEventById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ALERT_EVENT_NOT_FOUND));
        event.acknowledge(operatorOf(command), LocalDateTime.now());
        if (StringUtils.hasText(command.getRemark())) {
            event.setRemark(command.getRemark());
        }
        alertRepository.updateEvent(event);
        return toEventView(event);
    }

    @Override
    @Transactional
    public AlertEventView close(Long id, AlertHandleCommand command) {
        AlertEvent event = alertRepository.findEventById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ALERT_EVENT_NOT_FOUND));
        event.close(operatorOf(command), LocalDateTime.now());
        if (StringUtils.hasText(command.getRemark())) {
            event.setRemark(command.getRemark());
        }
        alertRepository.updateEvent(event);
        return toEventView(event);
    }

    /**
     * 生成规则对应的候选预警对象。
     *
     * @param rule 规则
     * @param today 当天
     * @return 候选
     */
    private List<Candidate> candidates(AlertRule rule, LocalDate today) {
        return switch (rule.getRuleType()) {
            case AnalyticsStatus.RULE_STOCK_LOW -> stockCandidates(rule);
            case AnalyticsStatus.RULE_RECEIVABLE_OVERDUE -> receivableCandidates(rule, today);
            case AnalyticsStatus.RULE_QUALITY_OPEN_NCR -> qualityCandidates(rule, today);
            default -> new ArrayList<>();
        };
    }

    /**
     * 库存不足候选。
     *
     * @param rule 规则
     * @return 候选
     */
    private List<Candidate> stockCandidates(AlertRule rule) {
        List<Candidate> candidates = new ArrayList<>();
        for (StockFact fact : analyticsRepository.listStockFacts()) {
            BigDecimal quantity = fact.getQuantity() == null ? BigDecimal.ZERO : fact.getQuantity();
            if (quantity.compareTo(rule.getThresholdValue()) <= 0) {
                candidates.add(new Candidate(fact.getItemCode() + "#" + fact.getWarehouseId(), quantity));
            }
        }
        return candidates;
    }

    /**
     * 超期应收候选。
     *
     * @param rule 规则
     * @param today 当天
     * @return 候选
     */
    private List<Candidate> receivableCandidates(AlertRule rule, LocalDate today) {
        List<Candidate> candidates = new ArrayList<>();
        for (ReceivableFact fact : analyticsRepository.listReceivableFacts()) {
            if (fact.getDueDate() == null || fact.outstanding().signum() <= 0) {
                continue;
            }
            long overdueDays = ChronoUnit.DAYS.between(fact.getDueDate(), today);
            if (overdueDays > 0
                    && BigDecimal.valueOf(overdueDays).compareTo(rule.getThresholdValue()) > 0) {
                candidates.add(new Candidate(fact.getDocNo(), BigDecimal.valueOf(overdueDays)));
            }
        }
        return candidates;
    }

    /**
     * 质量异常候选。
     *
     * @param rule 规则
     * @param today 当天
     * @return 候选
     */
    private List<Candidate> qualityCandidates(AlertRule rule, LocalDate today) {
        List<Candidate> candidates = new ArrayList<>();
        for (QualityFact fact : analyticsRepository.listQualityFacts()) {
            if ("CLOSED".equals(fact.getStatus()) || fact.getCreatedAt() == null) {
                continue;
            }
            long openDays = ChronoUnit.DAYS.between(fact.getCreatedAt().toLocalDate(), today);
            if (BigDecimal.valueOf(openDays).compareTo(rule.getThresholdValue()) > 0) {
                candidates.add(new Candidate(fact.getNcrNo(), BigDecimal.valueOf(openDays)));
            }
        }
        return candidates;
    }

    /**
     * 生成统计周期编码。
     *
     * @param periodType 周期类型
     * @param date 日期
     * @return 周期编码
     */
    private String periodCode(String periodType, LocalDate date) {
        return switch (periodType) {
            case AnalyticsStatus.PERIOD_WEEK -> String.format("%04dW%02d",
                    date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case AnalyticsStatus.PERIOD_MONTH -> YearMonth.from(date).format(DateTimeFormatter.ofPattern("yyyyMM"));
            default -> date.format(DateTimeFormatter.BASIC_ISO_DATE);
        };
    }

    /**
     * 操作人兜底。
     *
     * @param command 命令
     * @return 操作人
     */
    private String operatorOf(AlertHandleCommand command) {
        return StringUtils.hasText(command.getOperator()) ? command.getOperator() : DEFAULT_OPERATOR;
    }

    /**
     * 命令转规则。
     *
     * @param command 命令
     * @return 规则
     */
    private AlertRule toRule(AlertRuleSaveCommand command) {
        AlertRule rule = new AlertRule();
        rule.setRuleNo(command.getRuleNo());
        rule.setRuleName(command.getRuleName());
        rule.setRuleType(command.getRuleType());
        rule.setThresholdValue(command.getThresholdValue());
        rule.setPeriodType(command.getPeriodType());
        rule.setLevel(command.getLevel());
        rule.setSuggestion(command.getSuggestion());
        rule.setStatus(command.getStatus());
        rule.setRemark(command.getRemark());
        return rule;
    }

    /**
     * 规则转视图。
     *
     * @param rule 规则
     * @return 视图
     */
    private AlertRuleView toRuleView(AlertRule rule) {
        AlertRuleView view = new AlertRuleView();
        view.setRuleNo(rule.getRuleNo());
        view.setRuleName(rule.getRuleName());
        view.setRuleType(rule.getRuleType());
        view.setThresholdValue(rule.getThresholdValue());
        view.setPeriodType(rule.getPeriodType());
        view.setLevel(rule.getLevel());
        view.setSuggestion(rule.getSuggestion());
        view.setStatus(rule.getStatus());
        view.setRemark(rule.getRemark());
        return view;
    }

    /**
     * 事件转视图。
     *
     * @param event 事件
     * @return 视图
     */
    private AlertEventView toEventView(AlertEvent event) {
        AlertEventView view = new AlertEventView();
        view.setId(event.getId());
        view.setEventNo(event.getEventNo());
        view.setRuleNo(event.getRuleNo());
        view.setRuleType(event.getRuleType());
        view.setObjectKey(event.getObjectKey());
        view.setPeriodCode(event.getPeriodCode());
        view.setMetricValue(event.getMetricValue());
        view.setThresholdValue(event.getThresholdValue());
        view.setLevel(event.getLevel());
        view.setStatus(event.getStatus());
        view.setSuggestion(event.getSuggestion());
        view.setTriggeredAt(event.getTriggeredAt());
        view.setAcknowledgedBy(event.getAcknowledgedBy());
        view.setAcknowledgedAt(event.getAcknowledgedAt());
        view.setClosedBy(event.getClosedBy());
        view.setClosedAt(event.getClosedAt());
        view.setRemark(event.getRemark());
        return view;
    }

    /**
     * 候选预警对象。
     */
    private static final class Candidate {

        /** 对象键 */
        private final String objectKey;

        /** 实际值 */
        private final BigDecimal metric;

        /**
         * 构造候选。
         *
         * @param objectKey 对象键
         * @param metric 实际值
         */
        private Candidate(String objectKey, BigDecimal metric) {
            this.objectKey = objectKey;
            this.metric = metric;
        }
    }
}
