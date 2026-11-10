package com.bluewhale.crm.analytics.domain.service;

import com.bluewhale.crm.analytics.domain.model.ActionCandidate;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemStatus;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.analytics.domain.model.ActionRuleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 行动计划规则引擎（纯 Java，无框架依赖）。
 *
 * <p>去重键口径：</p>
 * <ul>
 *   <li>{@code KEY_CUSTOMER_IDLE:{customerId}:{yyyy-MM}}</li>
 *   <li>{@code OPP_OVERDUE:{opportunityId}:{yyyy-MM}}</li>
 *   <li>{@code TARGET_GAP:{targetId}}</li>
 *   <li>{@code CONTRACT_EXPIRE:{contractId}}</li>
 *   <li>{@code AR_OVERDUE:{customerId}:{yyyy-MM}}</li>
 *   <li>{@code CSAT_LOW:{ticketId}}</li>
 * </ul>
 * <p>已存在去重键（无论 OPEN 还是 CLOSED）均不再生成，保证关闭后不重复打扰。</p>
 *
 * @author BlueWhale
 */
public class ActionRuleEngine {

    /** 月份格式 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 标题最大长度 */
    private static final int TITLE_MAX_LENGTH = 255;

    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "P2";

    /**
     * 生成行动项。
     *
     * @param rule 规则
     * @param candidates 候选数据
     * @param existingDedupKeys 已存在的去重键（含已关闭）
     * @param statDate 统计日
     * @return 待新增行动项
     */
    public List<ActionItem> generate(ActionRule rule, List<ActionCandidate> candidates,
                                     Set<String> existingDedupKeys, LocalDate statDate) {
        if (rule == null || rule.getRuleType() == null || candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> existing = existingDedupKeys == null ? Collections.emptySet() : existingDedupKeys;
        List<ActionItem> items = new ArrayList<>();
        for (ActionCandidate candidate : candidates) {
            String dedupKey = dedupKey(rule.getRuleType(), candidate, statDate);
            if (dedupKey == null || existing.contains(dedupKey)) {
                continue;
            }
            items.add(toItem(rule, candidate, dedupKey, statDate));
        }
        return items;
    }

    /**
     * 计算去重键。
     *
     * @param ruleType 规则类型
     * @param candidate 候选数据
     * @param statDate 统计日
     * @return 去重键；关键 ID 缺失返回 null
     */
    public static String dedupKey(ActionRuleType ruleType, ActionCandidate candidate, LocalDate statDate) {
        if (ruleType == null || candidate == null) {
            return null;
        }
        String month = YearMonth.from(statDate).format(MONTH_FORMAT);
        return switch (ruleType) {
            case CUSTOMER_IDLE -> candidate.getCustomerId() == null ? null
                    : "KEY_CUSTOMER_IDLE:" + candidate.getCustomerId() + ":" + month;
            case OPPORTUNITY_OVERDUE -> candidate.getRefId() == null ? null
                    : "OPP_OVERDUE:" + candidate.getRefId() + ":" + month;
            case TARGET_GAP -> candidate.getRefId() == null ? null
                    : "TARGET_GAP:" + candidate.getRefId();
            case CONTRACT_EXPIRE -> candidate.getRefId() == null ? null
                    : "CONTRACT_EXPIRE:" + candidate.getRefId();
            case AR_OVERDUE -> candidate.getCustomerId() == null ? null
                    : "AR_OVERDUE:" + candidate.getCustomerId() + ":" + month;
            case CSAT_LOW -> candidate.getRefId() == null ? null
                    : "CSAT_LOW:" + candidate.getRefId();
        };
    }

    /**
     * 由候选数据构建行动项。
     *
     * @param rule 规则
     * @param candidate 候选数据
     * @param dedupKey 去重键
     * @param statDate 统计日
     * @return 行动项
     */
    private ActionItem toItem(ActionRule rule, ActionCandidate candidate, String dedupKey, LocalDate statDate) {
        ActionItem item = new ActionItem();
        item.setRuleCode(rule.getRuleCode());
        item.setRefType(candidate.getActionType());
        item.setRefId(candidate.getRefId());
        item.setCustomerId(candidate.getCustomerId());
        item.setCustomerName(candidate.getCustomerName());
        item.setTitle(truncate(title(rule.getRuleType(), candidate)));
        item.setSuggestion(truncate(suggestion(rule.getRuleType(), rule.getThresholdDays(), candidate)));
        item.setPriority(priority(candidate));
        item.setOwnerId(candidate.getOwnerId());
        item.setDeptId(candidate.getDeptId());
        item.setDueDate(dueDate(rule.getRuleType(), candidate, statDate));
        item.setStatus(ActionItemStatus.OPEN);
        item.setDedupKey(dedupKey);
        return item;
    }

    /**
     * 生成行动项标题。
     *
     * @param ruleType 规则类型
     * @param candidate 候选数据
     * @return 标题
     */
    private String title(ActionRuleType ruleType, ActionCandidate candidate) {
        String name = candidate.getTitle() == null ? "" : candidate.getTitle();
        return switch (ruleType) {
            case CUSTOMER_IDLE -> "客户「" + name + "」待跟进";
            case OPPORTUNITY_OVERDUE -> "商机「" + name + "」预计关闭已逾期";
            case TARGET_GAP -> "目标缺口 " + amountText(candidate.getMetricValue()) + " 元";
            case CONTRACT_EXPIRE -> "合同「" + name + "」即将到期";
            case AR_OVERDUE -> "客户「" + name + "」逾期应收";
            case CSAT_LOW -> "工单「" + name + "」满意度低分";
        };
    }

    /**
     * 生成行动建议。
     *
     * @param ruleType 规则类型
     * @param thresholdDays 阈值天数
     * @param candidate 候选数据
     * @return 建议
     */
    private String suggestion(ActionRuleType ruleType, Integer thresholdDays, ActionCandidate candidate) {
        int threshold = thresholdDays == null ? 0 : thresholdDays;
        return switch (ruleType) {
            case CUSTOMER_IDLE -> "客户已超过 " + threshold + " 天未跟进，请安排电话或拜访并更新跟进记录。";
            case OPPORTUNITY_OVERDUE -> "商机预计关闭日期已过，请确认推进计划或更新阶段。";
            case TARGET_GAP -> "当前目标仍有缺口，请补充高概率商机或调整销售计划。";
            case CONTRACT_EXPIRE -> "合同即将到期，请在到期前完成续约评估并联系客户。";
            case AR_OVERDUE -> "逾期金额 " + amountText(candidate.getMetricValue()) + " 元，请按催收流程跟进。";
            case CSAT_LOW -> "客户满意度评分 " + amountText(candidate.getMetricValue()) + "，请回访并记录改进措施。";
        };
    }

    /**
     * 生成建议完成日期。
     *
     * @param ruleType 规则类型
     * @param candidate 候选数据
     * @param statDate 统计日
     * @return 完成日期
     */
    private LocalDate dueDate(ActionRuleType ruleType, ActionCandidate candidate, LocalDate statDate) {
        if (candidate.getDueDate() != null) {
            return candidate.getDueDate();
        }
        return switch (ruleType) {
            case CUSTOMER_IDLE -> statDate.plusDays(7);
            case OPPORTUNITY_OVERDUE, AR_OVERDUE -> statDate.plusDays(3);
            case TARGET_GAP -> YearMonth.from(statDate).atEndOfMonth();
            case CONTRACT_EXPIRE -> statDate.plusDays(30);
            case CSAT_LOW -> statDate.plusDays(2);
        };
    }

    /**
     * 解析候选优先级。
     *
     * @param candidate 候选数据
     * @return 优先级
     */
    private String priority(ActionCandidate candidate) {
        String priority = candidate.getPriority();
        return priority == null || priority.isBlank() ? DEFAULT_PRIORITY : priority.trim().toUpperCase();
    }

    /**
     * 金额文本。
     *
     * @param value 金额
     * @return 文本
     */
    private String amountText(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 截断文本到列宽。
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null || text.length() <= TITLE_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, TITLE_MAX_LENGTH);
    }
}
