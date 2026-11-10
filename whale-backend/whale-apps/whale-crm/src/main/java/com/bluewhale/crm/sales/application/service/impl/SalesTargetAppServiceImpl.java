package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.sales.application.command.SalesTargetSaveCommand;
import com.bluewhale.crm.sales.application.command.SalesTargetUpdateCommand;
import com.bluewhale.crm.sales.application.service.SalesTargetAppService;
import com.bluewhale.crm.sales.application.view.SalesTargetAdjustmentView;
import com.bluewhale.crm.sales.application.view.SalesTargetView;
import com.bluewhale.crm.sales.application.view.TargetCompletionView;
import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.model.SalesTarget;
import com.bluewhale.crm.sales.domain.model.SalesTargetAdjustment;
import com.bluewhale.crm.sales.domain.model.SalesTargetQuery;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.domain.repository.SalesTargetAdjustmentRepository;
import com.bluewhale.crm.sales.domain.repository.SalesTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 销售目标应用服务实现。
 *
 * <p>目标维护仅限全量数据范围角色（管理员 / 销售主管 / 分析师不授予维护权限），
 * 单人目标对本人可见；完成金额在合同台账 M3 上线前按同期赢单商机金额计算。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesTargetAppServiceImpl implements SalesTargetAppService {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SalesTargetRepository targetRepository;

    private final SalesTargetAdjustmentRepository adjustmentRepository;

    private final OpportunityRepository opportunityRepository;

    private final CrmProperties properties;

    @Override
    public PageResult<SalesTargetView> page(SalesTargetQuery query, CrmActor actor) {
        requireGate();
        applyScope(query, actor);
        long total = targetRepository.count(query);
        List<SalesTargetView> views = targetRepository.page(query).stream()
                .map(SalesTargetView::from).toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    public SalesTargetView detail(Long id, CrmActor actor) {
        requireGate();
        return SalesTargetView.from(requireTarget(id, actor));
    }

    @Override
    @Transactional
    public SalesTargetView create(SalesTargetSaveCommand command, CrmActor actor) {
        requireGate();
        requireManageScope(actor);
        ForecastPeriodType periodType = ForecastPeriodType.from(command.getPeriodType());
        String period = periodType.normalize(command.getPeriod());
        ForecastScopeType scopeType = ForecastScopeType.from(command.getScopeType());
        Long ownerId = 0L;
        Long deptId = 0L;
        if (scopeType == ForecastScopeType.DEPT) {
            if (command.getDeptId() == null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "部门范围必须指定部门");
            }
            deptId = command.getDeptId();
        } else {
            if (command.getOwnerId() == null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "个人范围必须指定负责人");
            }
            ownerId = command.getOwnerId();
        }
        if (targetRepository.findByPeriodScope(period, periodType, scopeType, ownerId, deptId) != null) {
            throw CrmErrorCode.exception(CrmErrorCode.TARGET_DUPLICATE);
        }
        SalesTarget target = new SalesTarget();
        target.setPeriod(period);
        target.setPeriodType(periodType);
        target.setScopeType(scopeType);
        target.setOwnerId(ownerId);
        target.setDeptId(deptId);
        target.setRemark(command.getRemark());
        target.setCreatedBy(actor.requireUserId());
        target.updateAmount(command.getTargetAmount(), command.getRemark());
        targetRepository.insert(target);
        log.info("销售目标已创建 targetId={} period={} scope={} owner={} dept={}",
                target.getId(), period, scopeType, ownerId, deptId);
        return SalesTargetView.from(target);
    }

    @Override
    @Transactional
    public SalesTargetView update(Long id, SalesTargetUpdateCommand command, CrmActor actor) {
        requireGate();
        requireManageScope(actor);
        SalesTarget target = requireTarget(id, actor);
        BigDecimal before = target.getTargetAmount() == null ? BigDecimal.ZERO : target.getTargetAmount();
        BigDecimal after = command.getTargetAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        boolean changed = before.compareTo(after) != 0;
        if (changed && (command.getReason() == null || command.getReason().isBlank())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "调整目标金额必须填写原因");
        }
        target.updateAmount(after, command.getRemark());
        if (!targetRepository.update(target)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "销售目标已被他人修改，请刷新后重试");
        }
        if (changed) {
            SalesTargetAdjustment adjustment = new SalesTargetAdjustment();
            adjustment.setTargetId(target.getId());
            adjustment.setBeforeAmount(before);
            adjustment.setAfterAmount(after);
            adjustment.setReason(command.getReason().trim());
            adjustment.setOperatorId(actor.requireUserId());
            adjustment.setAdjustedAt(LocalDateTime.now());
            adjustmentRepository.insert(adjustment);
        }
        return SalesTargetView.from(target);
    }

    @Override
    @Transactional
    public void delete(Long id, CrmActor actor) {
        requireGate();
        requireManageScope(actor);
        requireTarget(id, actor);
        targetRepository.deleteById(id);
    }

    @Override
    public TargetCompletionView completion(Long id, CrmActor actor) {
        requireGate();
        SalesTarget target = requireTarget(id, actor);
        List<Opportunity> opportunities = loadWonOpportunities(target);
        BigDecimal achieved = opportunities.stream()
                .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal targetAmount = target.getTargetAmount() == null ? BigDecimal.ZERO : target.getTargetAmount();
        BigDecimal rate = targetAmount.compareTo(BigDecimal.ZERO) > 0
                ? achieved.multiply(HUNDRED).divide(targetAmount, AMOUNT_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        TargetCompletionView view = new TargetCompletionView();
        view.setTargetId(target.getId());
        view.setPeriod(target.getPeriod());
        view.setPeriodType(target.getPeriodType() == null ? null : target.getPeriodType().name());
        view.setScopeType(target.getScopeType() == null ? null : target.getScopeType().name());
        view.setTargetAmount(targetAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        view.setAchievedAmount(achieved.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        view.setCompletionRate(rate);
        view.setAchievedCount(opportunities.size());
        return view;
    }

    @Override
    public List<SalesTargetAdjustmentView> adjustments(Long id, CrmActor actor) {
        requireGate();
        requireTarget(id, actor);
        return adjustmentRepository.listByTarget(id).stream()
                .map(SalesTargetAdjustmentView::from).toList();
    }

    /**
     * 加载目标周期内已赢单商机。
     *
     * @param target 销售目标
     * @return 已赢单商机
     */
    private List<Opportunity> loadWonOpportunities(SalesTarget target) {
        ForecastPeriodType periodType = target.getPeriodType();
        OpportunityQuery query = new OpportunityQuery();
        query.setExpectedCloseFrom(periodType.startDate(target.getPeriod()));
        query.setExpectedCloseTo(periodType.endDate(target.getPeriod()));
        query.setStage(OpportunityStage.WON.name());
        if (target.getScopeType() == ForecastScopeType.DEPT) {
            query.setDeptId(target.getDeptId());
        } else {
            query.setOwnerId(target.getOwnerId());
        }
        return opportunityRepository.list(query);
    }

    /**
     * 按数据范围收口查询条件。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     */
    private void applyScope(SalesTargetQuery query, CrmActor actor) {
        if (actor.allDataScope()) {
            return;
        }
        query.setOwnerId(actor.requireUserId());
        query.setDeptId(null);
        query.setScopeType(ForecastScopeType.USER.name());
    }

    /**
     * 校验目标维护数据范围。
     *
     * @param actor 当前操作人
     */
    private void requireManageScope(CrmActor actor) {
        actor.requireUserId();
        if (!actor.allDataScope()) {
            throw CrmErrorCode.exception(CrmErrorCode.DATA_SCOPE_DENIED);
        }
    }

    /**
     * 查询并校验目标可见性。
     *
     * @param id 目标 ID
     * @param actor 当前操作人
     * @return 销售目标
     */
    private SalesTarget requireTarget(Long id, CrmActor actor) {
        SalesTarget target = targetRepository.findById(id);
        if (target == null || !visible(target, actor)) {
            throw CrmErrorCode.exception(CrmErrorCode.TARGET_NOT_FOUND);
        }
        return target;
    }

    /**
     * 判断目标对当前操作人是否可见。
     *
     * @param target 销售目标
     * @param actor 当前操作人
     * @return 可见返回 true
     */
    private boolean visible(SalesTarget target, CrmActor actor) {
        if (actor.allDataScope()) {
            return true;
        }
        return target.getScopeType() == ForecastScopeType.USER
                && Objects.equals(target.getOwnerId(), actor.getUserId());
    }

    /**
     * 校验需求门禁。
     */
    private void requireGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }
}
