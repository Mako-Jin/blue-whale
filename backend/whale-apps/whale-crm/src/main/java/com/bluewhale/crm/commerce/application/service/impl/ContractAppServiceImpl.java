package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.command.ContractRenewalCommand;
import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.commerce.application.view.ContractView;
import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.model.ContractQuery;
import com.bluewhale.crm.commerce.domain.port.ContractProjection;
import com.bluewhale.crm.commerce.domain.port.ContractSyncPort;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.config.CrmContractProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 合同台账应用服务实现。
 *
 * <p>CRM 只保存合同服务台账投影，不复制正文；同步失败不影响本地已有台账，
 * 续约标记由 CRM 本地维护，不被投影覆盖。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractAppServiceImpl implements ContractAppService {

    /** 续约状态：待续约 */
    private static final String RENEWAL_PENDING = "PENDING";

    /** 续约状态：已续约 */
    private static final String RENEWAL_RENEWED = "RENEWED";

    /** 续约状态：不续约 */
    private static final String RENEWAL_NOT_RENEWED = "NOT_RENEWED";

    /** 默认业务类型 */
    private static final String DEFAULT_BIZ_TYPE = "CRM_CUSTOMER";

    private final ContractLedgerRepository contractLedgerRepository;

    private final ContractSyncPort contractSyncPort;

    private final CustomerAppService customerAppService;

    private final CrmProperties properties;

    private final CrmContractProperties contractProperties;

    @Override
    public PageResult<ContractView> page(ContractQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        List<ContractLedger> ledgers = contractLedgerRepository.page(query);
        long total = contractLedgerRepository.count(query);
        return PageResult.of(ledgers.stream().map(ContractView::from).toList(),
                total, query.getCurrent(), query.getSize());
    }

    @Override
    public ContractView detail(Long id, CrmActor actor) {
        assertGate();
        return ContractView.from(requireLedger(id, actor));
    }

    @Override
    public List<ContractView> expiring(Integer days, CrmActor actor) {
        assertGate();
        int window = days == null || days <= 0 ? contractProperties.getExpiringDays() : days;
        LocalDate today = LocalDate.now();
        Long ownerFilter = actor.allDataScope() ? null : actor.requireUserId();
        return contractLedgerRepository.findExpiring(today, today.plusDays(window), ownerFilter)
                .stream().map(ContractView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncByCustomer(Long customerId, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        CustomerDetailView detail = customerAppService.detail(customerId, actor);
        CustomerView customer = detail.getCustomer();
        List<ContractProjection> projections;
        try {
            projections = contractSyncPort.listByCustomer(customerId);
        } catch (BizException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_SYNC_FAILED, exception.getMessage());
        } catch (Exception exception) {
            log.warn("合同投影同步失败 customerId={}", customerId, exception);
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_SYNC_FAILED, exception.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        for (ContractProjection projection : projections) {
            ContractLedger ledger = contractLedgerRepository.findByContractId(projection.contractId());
            boolean created = ledger == null;
            if (created) {
                ledger = new ContractLedger();
                ledger.setContractId(projection.contractId());
                ledger.setCustomerId(customerId);
                ledger.setBizType(DEFAULT_BIZ_TYPE);
                ledger.setBizId(String.valueOf(customerId));
                ledger.setRenewalStatus(RENEWAL_PENDING);
            }
            applyProjection(ledger, projection, customer, now);
            if (created) {
                contractLedgerRepository.insert(ledger);
            } else {
                contractLedgerRepository.updateProjection(ledger);
            }
        }
        return projections.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reconcile() {
        assertGate();
        int total = 0;
        for (Long customerId : contractLedgerRepository.findCustomerIds()) {
            List<ContractProjection> projections;
            try {
                projections = contractSyncPort.listByCustomer(customerId);
            } catch (Exception exception) {
                log.warn("合同定时对账失败 customerId={}", customerId, exception);
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            for (ContractProjection projection : projections) {
                ContractLedger ledger = contractLedgerRepository.findByContractId(projection.contractId());
                if (ledger == null) {
                    ledger = new ContractLedger();
                    ledger.setContractId(projection.contractId());
                    ledger.setCustomerId(customerId);
                    ledger.setBizType(DEFAULT_BIZ_TYPE);
                    ledger.setBizId(String.valueOf(customerId));
                    ledger.setRenewalStatus(RENEWAL_PENDING);
                    applyProjectionFields(ledger, projection, now);
                    contractLedgerRepository.insert(ledger);
                } else {
                    applyProjectionFields(ledger, projection, now);
                    contractLedgerRepository.updateProjection(ledger);
                }
                total++;
            }
        }
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractView markRenewal(Long id, ContractRenewalCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        ContractLedger ledger = requireLedger(id, actor);
        String status = command == null || command.getRenewalStatus() == null
                ? null : command.getRenewalStatus().trim().toUpperCase(java.util.Locale.ROOT);
        if (!RENEWAL_PENDING.equals(status) && !RENEWAL_RENEWED.equals(status)
                && !RENEWAL_NOT_RENEWED.equals(status)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID,
                    "续约状态只允许 PENDING / RENEWED / NOT_RENEWED");
        }
        ledger.setRenewalStatus(status);
        ledger.setRenewalOwnerId(command.getRenewalOwnerId());
        ledger.setRenewalRemark(trimToNull(command.getRenewalRemark()));
        ledger.setRenewalAt(LocalDateTime.now());
        if (!contractLedgerRepository.updateRenewal(ledger)) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_NOT_FOUND);
        }
        return ContractView.from(ledger);
    }

    /**
     * 应用投影字段（保留本地续约字段）。
     *
     * @param ledger 台账
     * @param projection 合同投影
     * @param customer 客户
     * @param now 同步时间
     */
    private void applyProjection(ContractLedger ledger, ContractProjection projection,
                                 CustomerView customer, LocalDateTime now) {
        applyProjectionFields(ledger, projection, now);
        if (ledger.getOwnerId() == null) {
            ledger.setOwnerId(customer.getOwnerId());
        }
        if (ledger.getDeptId() == null) {
            ledger.setDeptId(customer.getDeptId());
        }
    }

    /**
     * 应用投影字段（不覆盖本地续约字段）。
     *
     * @param ledger 台账
     * @param projection 合同投影
     * @param now 同步时间
     */
    private void applyProjectionFields(ContractLedger ledger, ContractProjection projection, LocalDateTime now) {
        ledger.setContractNo(projection.contractNo());
        ledger.setTitle(projection.title());
        ledger.setCategory(projection.category());
        ledger.setAmount(projection.amount());
        ledger.setCurrency(projection.currency() == null ? "CNY" : projection.currency());
        ledger.setStatus(projection.status());
        ledger.setEffectiveDate(projection.effectiveDate());
        ledger.setExpireDate(projection.expireDate());
        ledger.setBizType(projection.bizType() == null ? DEFAULT_BIZ_TYPE : projection.bizType());
        ledger.setBizId(projection.bizId() == null ? String.valueOf(ledger.getCustomerId()) : projection.bizId());
        if (projection.ownerId() != null) {
            ledger.setOwnerId(projection.ownerId());
        }
        if (projection.deptId() != null) {
            ledger.setDeptId(projection.deptId());
        }
        ledger.setLastSyncAt(now);
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
    private void applyDataScope(ContractQuery query, CrmActor actor) {
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
    }

    /**
     * 加载台账并校验数据范围。
     *
     * @param id 台账 ID
     * @param actor 当前操作人
     * @return 台账
     */
    private ContractLedger requireLedger(Long id, CrmActor actor) {
        ContractLedger ledger = contractLedgerRepository.findById(id);
        if (ledger == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(ledger.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTRACT_NOT_FOUND);
        }
        return ledger;
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
