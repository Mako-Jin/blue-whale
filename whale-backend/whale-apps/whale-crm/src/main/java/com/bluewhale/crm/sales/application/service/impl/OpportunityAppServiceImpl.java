package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.sales.application.command.OpportunitySaveCommand;
import com.bluewhale.crm.sales.application.command.OpportunityStageCommand;
import com.bluewhale.crm.sales.application.service.OpportunityAppService;
import com.bluewhale.crm.sales.application.view.FunnelStageView;
import com.bluewhale.crm.sales.application.view.FunnelView;
import com.bluewhale.crm.sales.application.view.OpportunityStageHistoryView;
import com.bluewhale.crm.sales.application.view.OpportunityView;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.model.OpportunityStageHistory;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityStageHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 商机应用服务实现（M2）。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class OpportunityAppServiceImpl implements OpportunityAppService {

    /** 漏斗正向阶段顺序 */
    private static final List<OpportunityStage> FUNNEL_STAGES = List.of(
            OpportunityStage.CONTACT, OpportunityStage.PROPOSAL,
            OpportunityStage.NEGOTIATION, OpportunityStage.WON);

    /** 概率下限 */
    private static final BigDecimal MIN_PROBABILITY = BigDecimal.ZERO;

    /** 概率上限 */
    private static final BigDecimal MAX_PROBABILITY = new BigDecimal("100");

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final OpportunityRepository opportunityRepository;

    private final OpportunityStageHistoryRepository historyRepository;

    private final CustomerAppService customerAppService;

    private final CrmProperties properties;

    @Override
    public PageResult<OpportunityView> page(OpportunityQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        List<Opportunity> opportunities = opportunityRepository.page(query);
        long total = opportunityRepository.count(query);
        List<OpportunityView> views = opportunities.stream().map(OpportunityView::from).toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    public OpportunityView detail(Long id, CrmActor actor) {
        assertGate();
        return OpportunityView.from(requireOpportunity(id, actor));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpportunityView create(OpportunitySaveCommand command, CrmActor actor) {
        assertGate();
        if (command.getCustomerId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_CUSTOMER_REQUIRED);
        }
        customerAppService.detail(command.getCustomerId(), actor);
        LocalDateTime now = LocalDateTime.now();
        Opportunity opportunity = new Opportunity();
        opportunity.setCustomerId(command.getCustomerId());
        opportunity.setName(command.getName().trim());
        opportunity.setAmount(command.getAmount());
        opportunity.setStage(OpportunityStage.CONTACT);
        opportunity.setProbability(resolveProbability(command.getProbability(), OpportunityStage.CONTACT));
        opportunity.setSource(trimToNull(command.getSource()));
        opportunity.setCompetitor(trimToNull(command.getCompetitor()));
        opportunity.setNextActionAt(command.getNextActionAt());
        opportunity.setExpectedCloseDate(command.getExpectedCloseDate());
        opportunity.setOwnerId(resolveOwner(command.getOwnerId(), actor));
        opportunity.setDeptId(command.getDeptId());
        opportunity.setStageEnteredAt(now);
        opportunityRepository.insert(opportunity);
        appendHistory(opportunity, null, OpportunityStage.CONTACT, null, actor.requireUserId(), now);
        return OpportunityView.from(opportunity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpportunityView update(Long id, OpportunitySaveCommand command, CrmActor actor) {
        assertGate();
        Opportunity opportunity = requireOpportunity(id, actor);
        if (opportunity.closed()) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT);
        }
        opportunity.setName(command.getName().trim());
        opportunity.setAmount(command.getAmount());
        opportunity.setExpectedCloseDate(command.getExpectedCloseDate());
        opportunity.setSource(trimToNull(command.getSource()));
        opportunity.setCompetitor(trimToNull(command.getCompetitor()));
        opportunity.setNextActionAt(command.getNextActionAt());
        if (command.getProbability() != null) {
            opportunity.setProbability(validateProbability(command.getProbability()));
        }
        if (command.getOwnerId() != null && actor.allDataScope()) {
            opportunity.setOwnerId(command.getOwnerId());
            opportunity.setDeptId(command.getDeptId());
        }
        if (!opportunityRepository.update(opportunity)) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT, "商机已被他人修改，请刷新后重试");
        }
        return OpportunityView.from(opportunity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpportunityView changeStage(Long id, OpportunityStageCommand command, CrmActor actor) {
        assertGate();
        Opportunity opportunity = requireOpportunity(id, actor);
        OpportunityStage from = opportunity.getStage();
        OpportunityStage target = OpportunityStage.from(command.getStage());
        if (target == from) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        opportunity.transitionTo(target, command.getLostReason(), command.getAmount(), now);
        if (!opportunityRepository.update(opportunity)) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_STAGE_CONFLICT, "商机已被他人修改，请刷新后重试");
        }
        appendHistory(opportunity, from, target, opportunity.getLostReason(), actor.requireUserId(), now);
        return OpportunityView.from(opportunity);
    }

    @Override
    public List<OpportunityStageHistoryView> history(Long id, CrmActor actor) {
        assertGate();
        requireOpportunity(id, actor);
        return historyRepository.listByOpportunity(id).stream()
                .map(OpportunityStageHistoryView::from).toList();
    }

    @Override
    public FunnelView funnel(OpportunityQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        List<Opportunity> opportunities = opportunityRepository.list(query);
        Map<OpportunityStage, long[]> counters = new EnumMap<>(OpportunityStage.class);
        Map<OpportunityStage, BigDecimal> amounts = new EnumMap<>(OpportunityStage.class);
        for (OpportunityStage stage : OpportunityStage.values()) {
            counters.put(stage, new long[]{0L});
            amounts.put(stage, BigDecimal.ZERO);
        }
        for (Opportunity opportunity : opportunities) {
            OpportunityStage stage = opportunity.getStage();
            counters.get(stage)[0] = counters.get(stage)[0] + 1;
            if (opportunity.getAmount() != null) {
                amounts.put(stage, amounts.get(stage).add(opportunity.getAmount()));
            }
        }
        List<FunnelStageView> stages = new ArrayList<>();
        long previousCount = 0L;
        for (OpportunityStage stage : FUNNEL_STAGES) {
            long count = counters.get(stage)[0];
            FunnelStageView view = new FunnelStageView();
            view.setStage(stage.name());
            view.setCount(count);
            view.setAmount(amounts.get(stage));
            view.setConversionRate(previousCount == 0L ? HUNDRED
                    : BigDecimal.valueOf(count * 100L)
                    .divide(BigDecimal.valueOf(previousCount), 2, RoundingMode.HALF_UP));
            stages.add(view);
            previousCount = count;
        }
        FunnelView funnel = new FunnelView();
        funnel.setStages(stages);
        funnel.setLostCount(counters.get(OpportunityStage.LOST)[0]);
        funnel.setLostAmount(amounts.get(OpportunityStage.LOST));
        long totalCount = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (FunnelStageView view : stages) {
            totalCount += view.getCount();
            totalAmount = totalAmount.add(view.getAmount());
        }
        funnel.setTotalCount(totalCount);
        funnel.setTotalAmount(totalAmount);
        return funnel;
    }

    /**
     * 校验业务门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 应用数据范围。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     */
    private void applyDataScope(OpportunityQuery query, CrmActor actor) {
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
    }

    /**
     * 加载商机并校验数据范围。
     *
     * @param id 商机 ID
     * @param actor 当前操作人
     * @return 商机
     */
    private Opportunity requireOpportunity(Long id, CrmActor actor) {
        Opportunity opportunity = opportunityRepository.findById(id);
        if (opportunity == null) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_NOT_FOUND);
        }
        if (!actor.allDataScope() && !actor.requireUserId().equals(opportunity.getOwnerId())) {
            throw CrmErrorCode.exception(CrmErrorCode.OPPORTUNITY_NOT_FOUND);
        }
        return opportunity;
    }

    /**
     * 解析负责人。
     *
     * @param requested 请求负责人
     * @param actor 当前操作人
     * @return 负责人
     */
    private Long resolveOwner(Long requested, CrmActor actor) {
        return actor.allDataScope() && requested != null ? requested : actor.requireUserId();
    }

    /**
     * 解析概率：显式值校验后使用，否则按阶段默认值。
     *
     * @param requested 请求概率
     * @param stage 阶段
     * @return 概率
     */
    private BigDecimal resolveProbability(BigDecimal requested, OpportunityStage stage) {
        return requested == null ? stage.defaultProbability() : validateProbability(requested);
    }

    /**
     * 校验概率区间。
     *
     * @param probability 概率
     * @return 概率
     */
    private BigDecimal validateProbability(BigDecimal probability) {
        if (probability.compareTo(MIN_PROBABILITY) < 0 || probability.compareTo(MAX_PROBABILITY) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "赢单概率必须在 0–100 之间");
        }
        return probability;
    }

    /**
     * 追加阶段历史。
     *
     * @param opportunity 商机
     * @param from 原阶段
     * @param to 目标阶段
     * @param lostReason 输单原因
     * @param operatorId 操作人
     * @param at 时间
     * @return 阶段历史
     */
    private OpportunityStageHistory appendHistory(Opportunity opportunity, OpportunityStage from,
                                                  OpportunityStage to, String lostReason,
                                                  Long operatorId, LocalDateTime at) {
        OpportunityStageHistory history = new OpportunityStageHistory();
        history.setOpportunityId(opportunity.getId());
        history.setFromStage(from);
        history.setToStage(to);
        history.setAmount(opportunity.getAmount());
        history.setProbability(opportunity.getProbability());
        history.setLostReason(lostReason);
        history.setOperatorId(operatorId);
        history.setChangedAt(at);
        historyRepository.insert(history);
        return history;
    }

    /**
     * 去除首尾空白，空白返回 {@code null}。
     *
     * @param text 文本
     * @return 结果
     */
    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
