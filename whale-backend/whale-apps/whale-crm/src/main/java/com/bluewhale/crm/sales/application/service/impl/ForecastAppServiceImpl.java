package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.sales.application.command.ForecastAdjustCommand;
import com.bluewhale.crm.sales.application.command.ForecastGenerateCommand;
import com.bluewhale.crm.sales.application.service.ForecastAppService;
import com.bluewhale.crm.sales.application.view.ForecastAdjustmentView;
import com.bluewhale.crm.sales.application.view.ForecastSnapshotView;
import com.bluewhale.crm.sales.domain.model.ForecastAdjustment;
import com.bluewhale.crm.sales.domain.model.ForecastPeriodType;
import com.bluewhale.crm.sales.domain.model.ForecastQuery;
import com.bluewhale.crm.sales.domain.model.ForecastScopeType;
import com.bluewhale.crm.sales.domain.model.ForecastSnapshot;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.model.OpportunityQuery;
import com.bluewhale.crm.sales.domain.model.OpportunityStage;
import com.bluewhale.crm.sales.domain.repository.ForecastAdjustmentRepository;
import com.bluewhale.crm.sales.domain.repository.ForecastSnapshotRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
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
 * 销售预测应用服务实现。
 *
 * <p>快照由商机实时聚合生成，同周期同范围重复生成会覆盖（幂等），
 * 手工调整单独留痕；调整不影响商机本身。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastAppServiceImpl implements ForecastAppService {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 承诺口径的最低概率 */
    private static final BigDecimal COMMIT_PROBABILITY = new BigDecimal("90");

    private final ForecastSnapshotRepository snapshotRepository;

    private final ForecastAdjustmentRepository adjustmentRepository;

    private final OpportunityRepository opportunityRepository;

    private final CrmProperties properties;

    @Override
    public PageResult<ForecastSnapshotView> page(ForecastQuery query, CrmActor actor) {
        requireGate();
        applyScope(query, actor);
        long total = snapshotRepository.count(query);
        List<ForecastSnapshotView> views = snapshotRepository.page(query).stream()
                .map(ForecastSnapshotView::from).toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    public ForecastSnapshotView detail(Long id, CrmActor actor) {
        requireGate();
        return ForecastSnapshotView.from(requireSnapshot(id, actor));
    }

    @Override
    @Transactional
    public ForecastSnapshotView generate(ForecastGenerateCommand command, CrmActor actor) {
        requireGate();
        ForecastPeriodType periodType = ForecastPeriodType.from(command.getPeriodType());
        String period = periodType.normalize(command.getPeriod());
        ForecastScopeType scopeType = ForecastScopeType.from(command.getScopeType());
        Long ownerId = 0L;
        Long deptId = 0L;
        if (scopeType == ForecastScopeType.DEPT) {
            if (!actor.allDataScope()) {
                throw CrmErrorCode.exception(CrmErrorCode.DATA_SCOPE_DENIED);
            }
            if (command.getDeptId() == null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "部门范围必须指定部门");
            }
            deptId = command.getDeptId();
        } else {
            ownerId = actor.allDataScope() && command.getOwnerId() != null
                    ? command.getOwnerId() : actor.requireUserId();
        }
        ForecastAggregate aggregate = aggregate(loadOpportunities(periodType, period, scopeType, ownerId, deptId));
        LocalDateTime now = LocalDateTime.now();
        ForecastSnapshot snapshot = snapshotRepository.findByPeriodScope(
                period, periodType, scopeType, ownerId, deptId);
        if (snapshot == null) {
            snapshot = new ForecastSnapshot();
            snapshot.setPeriod(period);
            snapshot.setPeriodType(periodType);
            snapshot.setScopeType(scopeType);
            snapshot.setOwnerId(ownerId);
            snapshot.setDeptId(deptId);
            snapshot.setCreatedBy(actor.requireUserId());
            snapshot.applyAggregation(aggregate.pipeline, aggregate.best, aggregate.commit,
                    aggregate.weighted, aggregate.count, now);
            snapshotRepository.insert(snapshot);
        } else {
            snapshot.applyAggregation(aggregate.pipeline, aggregate.best, aggregate.commit,
                    aggregate.weighted, aggregate.count, now);
            if (!snapshotRepository.update(snapshot)) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "预测快照已被他人修改，请刷新后重试");
            }
        }
        log.info("销售预测快照已生成 snapshotId={} period={} scope={} owner={} dept={}",
                snapshot.getId(), period, scopeType, ownerId, deptId);
        return ForecastSnapshotView.from(snapshot);
    }

    @Override
    @Transactional
    public ForecastSnapshotView adjust(Long id, ForecastAdjustCommand command, CrmActor actor) {
        requireGate();
        ForecastSnapshot snapshot = requireSnapshot(id, actor);
        String fieldCode = ForecastSnapshot.normalizeField(command.getFieldCode());
        BigDecimal before = snapshot.amountOf(fieldCode);
        snapshot.adjust(fieldCode, command.getAmount());
        if (!snapshotRepository.update(snapshot)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "预测快照已被他人修改，请刷新后重试");
        }
        ForecastAdjustment adjustment = new ForecastAdjustment();
        adjustment.setSnapshotId(snapshot.getId());
        adjustment.setFieldCode(fieldCode);
        adjustment.setBeforeAmount(before);
        adjustment.setAfterAmount(snapshot.amountOf(fieldCode));
        adjustment.setReason(command.getReason().trim());
        adjustment.setOperatorId(actor.requireUserId());
        adjustment.setAdjustedAt(LocalDateTime.now());
        adjustmentRepository.insert(adjustment);
        return ForecastSnapshotView.from(snapshot);
    }

    @Override
    public List<ForecastAdjustmentView> adjustments(Long id, CrmActor actor) {
        requireGate();
        requireSnapshot(id, actor);
        return adjustmentRepository.listBySnapshot(id).stream()
                .map(ForecastAdjustmentView::from).toList();
    }

    /**
     * 加载周期内商机。
     *
     * @param periodType 周期类型
     * @param period 周期
     * @param scopeType 范围类型
     * @param ownerId 负责人
     * @param deptId 部门
     * @return 商机列表
     */
    private List<Opportunity> loadOpportunities(ForecastPeriodType periodType, String period,
                                                ForecastScopeType scopeType, Long ownerId, Long deptId) {
        OpportunityQuery query = new OpportunityQuery();
        query.setExpectedCloseFrom(periodType.startDate(period));
        query.setExpectedCloseTo(periodType.endDate(period));
        if (scopeType == ForecastScopeType.DEPT) {
            query.setDeptId(deptId);
        } else {
            query.setOwnerId(ownerId);
        }
        return opportunityRepository.list(query);
    }

    /**
     * 汇总商机预测口径。
     *
     * @param opportunities 商机列表
     * @return 汇总结果
     */
    private ForecastAggregate aggregate(List<Opportunity> opportunities) {
        BigDecimal pipeline = BigDecimal.ZERO;
        BigDecimal best = BigDecimal.ZERO;
        BigDecimal commit = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        int count = 0;
        for (Opportunity opportunity : opportunities) {
            BigDecimal amount = opportunity.getAmount() == null ? BigDecimal.ZERO : opportunity.getAmount();
            if (opportunity.getStage() == OpportunityStage.WON) {
                commit = commit.add(amount);
                weighted = weighted.add(amount);
                continue;
            }
            if (opportunity.getStage() == OpportunityStage.LOST) {
                continue;
            }
            count++;
            pipeline = pipeline.add(amount);
            if (opportunity.getStage() == OpportunityStage.NEGOTIATION) {
                best = best.add(amount);
            }
            BigDecimal probability = opportunity.getProbability() == null
                    ? BigDecimal.ZERO : opportunity.getProbability();
            if (probability.compareTo(COMMIT_PROBABILITY) >= 0) {
                commit = commit.add(amount);
            }
            weighted = weighted.add(amount.multiply(probability).divide(HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP));
        }
        return new ForecastAggregate(pipeline, best, commit, weighted, count);
    }

    /**
     * 按数据范围收口查询条件。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     */
    private void applyScope(ForecastQuery query, CrmActor actor) {
        if (actor.allDataScope()) {
            return;
        }
        query.setOwnerId(actor.requireUserId());
        query.setDeptId(null);
        query.setScopeType(ForecastScopeType.USER.name());
    }

    /**
     * 查询并校验快照可见性。
     *
     * @param id 快照 ID
     * @param actor 当前操作人
     * @return 快照
     */
    private ForecastSnapshot requireSnapshot(Long id, CrmActor actor) {
        ForecastSnapshot snapshot = snapshotRepository.findById(id);
        if (snapshot == null || !visible(snapshot, actor)) {
            throw CrmErrorCode.exception(CrmErrorCode.FORECAST_NOT_FOUND);
        }
        return snapshot;
    }

    /**
     * 判断快照对当前操作人是否可见。
     *
     * @param snapshot 快照
     * @param actor 当前操作人
     * @return 可见返回 true
     */
    private boolean visible(ForecastSnapshot snapshot, CrmActor actor) {
        if (actor.allDataScope()) {
            return true;
        }
        return snapshot.getScopeType() == ForecastScopeType.USER
                && Objects.equals(snapshot.getOwnerId(), actor.getUserId());
    }

    /**
     * 校验需求门禁。
     */
    private void requireGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 预测聚合结果。
     */
    private static final class ForecastAggregate {

        /** 管道金额 */
        private final BigDecimal pipeline;

        /** 最佳金额 */
        private final BigDecimal best;

        /** 承诺金额 */
        private final BigDecimal commit;

        /** 加权金额 */
        private final BigDecimal weighted;

        /** 未关闭商机数 */
        private final int count;

        /**
         * 构造聚合结果。
         *
         * @param pipeline 管道金额
         * @param best 最佳金额
         * @param commit 承诺金额
         * @param weighted 加权金额
         * @param count 未关闭商机数
         */
        private ForecastAggregate(BigDecimal pipeline, BigDecimal best, BigDecimal commit,
                                  BigDecimal weighted, int count) {
            this.pipeline = pipeline;
            this.best = best;
            this.commit = commit;
            this.weighted = weighted;
            this.count = count;
        }
    }
}
