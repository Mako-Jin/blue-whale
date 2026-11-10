package com.bluewhale.crm.commerce.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.view.ArSnapshotView;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.commerce.domain.model.ArSnapshot;
import com.bluewhale.crm.commerce.domain.model.ArSnapshotQuery;
import com.bluewhale.crm.commerce.domain.port.ErpReceivablePort;
import com.bluewhale.crm.commerce.domain.port.ErpReceivableSummary;
import com.bluewhale.crm.commerce.domain.repository.ArSnapshotRepository;
import com.bluewhale.crm.commerce.domain.repository.ContractLedgerRepository;
import com.bluewhale.crm.config.CrmArProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 客户应收对账应用服务实现。
 *
 * <p>快照来自 ERP 只读契约；ERP 不可用时逐客户降级计数，不中断整体同步，也不抛 500。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArReconcileAppServiceImpl implements ArReconcileAppService {

    /** 定时任务使用的系统操作人（全量数据范围） */
    private static final CrmActor SYSTEM_ACTOR = new CrmActor(1L, "system", Set.of("crm_admin"), Set.of());

    /** 数据来源 */
    private static final String SOURCE_ERP = "ERP";

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final ArSnapshotRepository arSnapshotRepository;

    private final ContractLedgerRepository contractLedgerRepository;

    private final CustomerAppService customerAppService;

    private final ErpReceivablePort erpReceivablePort;

    private final CrmArProperties arProperties;

    private final CrmProperties properties;

    @Override
    public PageResult<ArSnapshotView> page(ArSnapshotQuery query, CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.requireUserId());
        }
        List<ArSnapshot> snapshots = arSnapshotRepository.page(query);
        long total = arSnapshotRepository.count(query);
        return PageResult.of(snapshots.stream().map(ArSnapshotView::from).toList(),
                total, query.getCurrent(), query.getSize());
    }

    @Override
    public ArSyncResultView sync(CrmActor actor) {
        assertGate();
        actor.requireUserId();
        return doSync();
    }

    @Override
    public ArSyncResultView syncAll() {
        if (!properties.isDemandGatePassed()) {
            return ArSyncResultView.of(0, 0, 0, "需求门禁未通过，跳过应收对账");
        }
        return doSync();
    }

    /**
     * 执行同步：按有合同台账的客户拉取 ERP 未结应收并幂等保存当日快照。
     *
     * @return 同步结果
     */
    private ArSyncResultView doSync() {
        if (!arProperties.isEnabled()) {
            return ArSyncResultView.of(0, 0, 0, "应收对账未启用");
        }
        List<Long> customerIds = contractLedgerRepository.findCustomerIds();
        if (customerIds.size() > arProperties.getBatchSize()) {
            customerIds = customerIds.subList(0, arProperties.getBatchSize());
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int synced = 0;
        int skipped = 0;
        int failed = 0;
        for (Long customerId : customerIds) {
            try {
                CustomerView customer = customerAppService.detail(customerId, SYSTEM_ACTOR).getCustomer();
                if (customer == null || !StringUtils.hasText(customer.getCustomerCode())) {
                    skipped++;
                    continue;
                }
                ErpReceivableSummary summary = erpReceivablePort.fetchByCustomerCode(customer.getCustomerCode());
                if (summary == null || summary.empty()) {
                    skipped++;
                    continue;
                }
                arSnapshotRepository.upsert(buildSnapshot(customer, summary, today, now));
                synced++;
            } catch (Exception exception) {
                failed++;
                log.warn("应收对账失败 customerId={}", customerId, exception);
            }
        }
        return ArSyncResultView.of(synced, skipped, failed,
                "成功 " + synced + " / 跳过 " + skipped + " / 失败 " + failed);
    }

    /**
     * 构建快照。
     *
     * @param customer 客户
     * @param summary ERP 应收汇总
     * @param today 快照日期
     * @param now 同步时间
     * @return 快照
     */
    private ArSnapshot buildSnapshot(CustomerView customer, ErpReceivableSummary summary,
                                     LocalDate today, LocalDateTime now) {
        ArSnapshot snapshot = new ArSnapshot();
        snapshot.setCustomerId(customer.getId());
        snapshot.setCustomerCode(customer.getCustomerCode());
        snapshot.setCustomerName(customer.getName());
        snapshot.setSnapshotDate(today);
        snapshot.setCurrency(StringUtils.hasText(summary.currency()) ? summary.currency() : DEFAULT_CURRENCY);
        snapshot.setReceivableCount(summary.receivableCount() == null ? 0 : summary.receivableCount());
        snapshot.setTotalRemaining(amount(summary.totalRemaining()));
        snapshot.setNotDue(amount(summary.notDue()));
        snapshot.setOverdue1To30(amount(summary.overdue1To30()));
        snapshot.setOverdue31To60(amount(summary.overdue31To60()));
        snapshot.setOverdue61To90(amount(summary.overdue61To90()));
        snapshot.setOverdueOver90(amount(summary.overdueOver90()));
        snapshot.setSource(SOURCE_ERP);
        snapshot.setOwnerId(customer.getOwnerId());
        snapshot.setDeptId(customer.getDeptId());
        snapshot.setLastSyncAt(now);
        return snapshot;
    }

    /**
     * 金额兜底为 0。
     *
     * @param value 金额
     * @return 非空金额
     */
    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 校验业务门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }
}
