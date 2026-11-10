package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.CollectionActionCommand;
import com.bluewhale.crm.commerce.application.command.CollectionInstallmentCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPlanSaveCommand;
import com.bluewhale.crm.commerce.application.command.CollectionPromiseCommand;
import com.bluewhale.crm.commerce.application.service.CollectionAppService;
import com.bluewhale.crm.commerce.application.view.CollectionActionView;
import com.bluewhale.crm.commerce.application.view.CollectionPlanView;
import com.bluewhale.crm.commerce.domain.model.CollectionAction;
import com.bluewhale.crm.commerce.domain.model.CollectionPlan;
import com.bluewhale.crm.commerce.domain.model.CollectionPlanStatus;
import com.bluewhale.crm.commerce.domain.model.CollectionPriority;
import com.bluewhale.crm.commerce.domain.model.CollectionQuery;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.repository.CollectionActionRepository;
import com.bluewhale.crm.commerce.domain.repository.CollectionPlanRepository;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.config.CrmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 回款计划应用服务实现。
 *
 * <p>分期合计必须等于合同可回款金额；实收与对账由 ERP 维护，本服务不写实收。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CollectionAppServiceImpl implements CollectionAppService {

    /** 催收金额容差 */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    /** 允许的催收动作 */
    private static final Set<String> ACTION_TYPES =
            Set.of("CALL", "VISIT", "EMAIL", "REMINDER", "PROMISE");

    /** 承诺还款动作 */
    private static final String ACTION_PROMISE = "PROMISE";

    private final CollectionPlanRepository collectionPlanRepository;

    private final CollectionActionRepository collectionActionRepository;

    private final ContractLedgerRepository contractLedgerRepository;

    private final CrmProperties properties;

    @Override
    public PageResult<CollectionPlanView> page(CollectionQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        List<CollectionPlan> plans = collectionPlanRepository.page(query);
        long total = collectionPlanRepository.count(query);
        return PageResult.of(plans.stream().map(CollectionPlanView::from).toList(),
                total, query.getCurrent(), query.getSize());
    }

    @Override
    public CollectionPlanView detail(Long id, CrmActor actor) {
        assertGate();
        return CollectionPlanView.from(requirePlan(id, actor));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CollectionPlanView> savePlan(CollectionPlanSaveCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        if (command == null || command.getContractId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "必须指定合同台账");
        }
        if (command.getInstallments() == null || command.getInstallments().isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_PLAN_INVALID, "回款计划至少需要一个分期");
        }
        ContractLedger contract = requireContract(command.getContractId(), actor);
        List<CollectionPlan> plans = buildPlans(command.getInstallments());
        BigDecimal total = plans.stream()
                .map(CollectionPlan::getPlannedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (contract.getAmount() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_AMOUNT_MISMATCH,
                    "合同金额尚未同步，无法核对回款分期合计");
        }
        if (total.subtract(contract.getAmount()).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_AMOUNT_MISMATCH,
                    "回款分期合计 " + total + " 与合同金额 " + contract.getAmount() + " 不一致");
        }
        Long ownerId = contract.getOwnerId() == null ? actor.requireUserId() : contract.getOwnerId();
        List<CollectionPlan> saved = collectionPlanRepository.replaceByContract(
                contract.getId(), contract.getCustomerId(), ownerId, contract.getDeptId(), plans);
        return saved.stream().map(CollectionPlanView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionPlanView promise(Long id, CollectionPromiseCommand command, CrmActor actor) {
        assertGate();
        Long operatorId = actor.requireUserId();
        CollectionPlan plan = requirePlan(id, actor);
        requireOpen(plan);
        plan.setPromiseDate(command.getPromiseDate());
        plan.setPromiseAmount(command.getPromiseAmount());
        plan.setRemark(trimToNull(command.getRemark()));
        if (!collectionPlanRepository.updatePromise(plan)) {
            throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_NOT_FOUND);
        }
        CollectionAction action = new CollectionAction();
        action.setPlanId(plan.getId());
        action.setContractId(plan.getContractId());
        action.setActionType(ACTION_PROMISE);
        action.setContent(trimToNull(command.getRemark()));
        action.setPromiseDate(command.getPromiseDate());
        action.setPromiseAmount(command.getPromiseAmount());
        action.setOperatorId(operatorId);
        collectionActionRepository.insert(action);
        return CollectionPlanView.from(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionActionView addAction(Long id, CollectionActionCommand command, CrmActor actor) {
        assertGate();
        Long operatorId = actor.requireUserId();
        CollectionPlan plan = requirePlan(id, actor);
        String type = command == null || command.getActionType() == null
                ? null : command.getActionType().trim().toUpperCase(java.util.Locale.ROOT);
        if (type == null || !ACTION_TYPES.contains(type)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID,
                    "催收动作只允许 CALL / VISIT / EMAIL / REMINDER / PROMISE");
        }
        CollectionAction action = new CollectionAction();
        action.setPlanId(plan.getId());
        action.setContractId(plan.getContractId());
        action.setActionType(type);
        action.setContent(trimToNull(command.getContent()));
        action.setPromiseDate(command.getPromiseDate());
        action.setPromiseAmount(command.getPromiseAmount());
        action.setOperatorId(operatorId);
        collectionActionRepository.insert(action);
        if (ACTION_PROMISE.equals(type) && command.getPromiseDate() != null
                && command.getPromiseAmount() != null) {
            plan.setPromiseDate(command.getPromiseDate());
            plan.setPromiseAmount(command.getPromiseAmount());
            collectionPlanRepository.updatePromise(plan);
        }
        return CollectionActionView.from(action);
    }

    @Override
    public List<CollectionActionView> actions(Long id, CrmActor actor) {
        assertGate();
        CollectionPlan plan = requirePlan(id, actor);
        return collectionActionRepository.findByPlan(plan.getId())
                .stream().map(CollectionActionView::from).toList();
    }

    /**
     * 构建回款分期。
     *
     * @param commands 分期命令
     * @return 分期列表
     */
    private List<CollectionPlan> buildPlans(List<CollectionInstallmentCommand> commands) {
        List<CollectionPlan> plans = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            CollectionInstallmentCommand command = commands.get(index);
            if (command == null || command.getPlannedAmount() == null || command.getPlannedDate() == null) {
                throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_PLAN_INVALID,
                        "分期金额与计划回款日期不能为空");
            }
            if (command.getPlannedAmount().signum() <= 0) {
                throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_PLAN_INVALID,
                        "分期金额必须大于 0");
            }
            String override = trimToNull(command.getPriorityOverride());
            if (override != null && CollectionPriority.from(override) == null) {
                throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_PLAN_INVALID,
                        "催收优先级只允许 HIGH / MEDIUM / LOW");
            }
            CollectionPlan plan = new CollectionPlan();
            plan.setInstallmentNo(index + 1);
            plan.setPlannedAmount(command.getPlannedAmount());
            plan.setPlannedDate(command.getPlannedDate());
            plan.setStatus(CollectionPlanStatus.OPEN);
            plan.setPriorityOverride(override == null ? null : override.toUpperCase(java.util.Locale.ROOT));
            plan.setRemark(trimToNull(command.getRemark()));
            plans.add(plan);
        }
        return plans;
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
    private void applyDataScope(CollectionQuery query, CrmActor actor) {
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
    }

    /**
     * 加载计划并校验数据范围。
     *
     * @param id 计划 ID
     * @param actor 当前操作人
     * @return 计划
     */
    private CollectionPlan requirePlan(Long id, CrmActor actor) {
        CollectionPlan plan = collectionPlanRepository.findById(id);
        if (plan == null) {
            throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(plan.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_NOT_FOUND);
        }
        return plan;
    }

    /**
     * 加载合同并校验数据范围。
     *
     * @param id 合同台账 ID
     * @param actor 当前操作人
     * @return 合同台账
     */
    private ContractLedger requireContract(Long id, CrmActor actor) {
        ContractLedger contract = contractLedgerRepository.findById(id);
        if (contract == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(contract.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_NOT_FOUND);
        }
        return contract;
    }

    /**
     * 校验计划可登记承诺与催收。
     *
     * @param plan 计划
     */
    private void requireOpen(CollectionPlan plan) {
        if (plan.getStatus() != CollectionPlanStatus.OPEN) {
            throw CrmErrorCode.exception(CrmErrorCode.COLLECTION_STATE_CONFLICT);
        }
    }

    /**
     * 去空白并转空串为 {@code null}。
     *
     * @param value 原始值
     * @return 处理后的值
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
