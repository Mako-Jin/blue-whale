package com.bluewhale.party.integration.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.integration.application.view.EmployeeSyncResultView;
import com.bluewhale.party.integration.domain.gateway.ErpEmployeeGateway;
import com.bluewhale.party.integration.domain.model.EmployeeSnapshot;
import com.bluewhale.party.integration.domain.model.ErpDirectoryPage;
import com.bluewhale.party.integration.domain.model.ErpEmployeeEntry;
import com.bluewhale.party.integration.domain.repository.EmployeeSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工快照应用服务：空目录拒覆盖、失败保留旧快照、分页 upsert。
 *
 * @author BlueWhale
 */
class EmployeeSnapshotAppServiceImplTest {

    private EmployeeSnapshotRepository employeeSnapshotRepository;

    private ErpEmployeeGateway erpEmployeeGateway;

    private EmployeeSnapshotAppServiceImpl service;

    private PartyActor actor;

    @BeforeEach
    void setUp() {
        employeeSnapshotRepository = mock(EmployeeSnapshotRepository.class);
        erpEmployeeGateway = mock(ErpEmployeeGateway.class);
        service = new EmployeeSnapshotAppServiceImpl(employeeSnapshotRepository,
                erpEmployeeGateway, new PartyProperties());
        actor = new PartyActor(9L, "padmin", Set.of("party_admin"), Set.of());
    }

    @Test
    @DisplayName("源目录为空且无旧快照时拒绝同步")
    void emptySourceWithoutOldSnapshotShouldFail() {
        when(erpEmployeeGateway.fetchPage(anyLong(), anyLong())).thenReturn(page(0, List.of()));
        when(employeeSnapshotRepository.countActive()).thenReturn(0L);

        BizException exception = assertThrows(BizException.class, () -> service.sync(actor));
        assertEquals(PartyErrorCode.EMPLOYEE_SYNC_EMPTY, exception.getCode());
    }

    @Test
    @DisplayName("源目录为空但已有旧快照时保留旧快照")
    void emptySourceShouldKeepOldSnapshot() {
        when(erpEmployeeGateway.fetchPage(anyLong(), anyLong())).thenReturn(page(0, List.of()));
        when(employeeSnapshotRepository.countActive()).thenReturn(5L);

        EmployeeSyncResultView result = service.sync(actor);

        assertEquals(0L, result.getSourceTotal());
        assertEquals(5L, result.getActiveTotal());
        verify(employeeSnapshotRepository, never()).save(any(EmployeeSnapshot.class));
    }

    @Test
    @DisplayName("ERP 拉取失败时旧快照不被覆盖")
    void gatewayFailureShouldKeepOldSnapshot() {
        when(erpEmployeeGateway.fetchPage(anyLong(), anyLong()))
                .thenThrow(PartyErrorCode.exception(PartyErrorCode.EMPLOYEE_SYNC_FAILED));

        BizException exception = assertThrows(BizException.class, () -> service.sync(actor));
        assertEquals(PartyErrorCode.EMPLOYEE_SYNC_FAILED, exception.getCode());
        verify(employeeSnapshotRepository, never()).save(any(EmployeeSnapshot.class));
    }

    @Test
    @DisplayName("分页拉取逐条 upsert：新增与更新分别计数")
    void syncShouldUpsertPagedRecords() {
        when(erpEmployeeGateway.fetchPage(1L, 500L)).thenReturn(page(2, List.of(
                entry("E001"), entry("E002"))));
        when(employeeSnapshotRepository.findByEmployeeNo("E001"))
                .thenReturn(Optional.of(existing(11L)));
        when(employeeSnapshotRepository.findByEmployeeNo("E002")).thenReturn(Optional.empty());
        when(employeeSnapshotRepository.countActive()).thenReturn(5L);

        EmployeeSyncResultView result = service.sync(actor);

        assertEquals(2L, result.getSourceTotal());
        assertEquals(1, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(0, result.getSkipped());
        assertEquals(5L, result.getActiveTotal());
    }

    /**
     * 构造 ERP 分页。
     *
     * @param total 总条数
     * @param records 条目
     * @return 分页
     */
    private ErpDirectoryPage page(long total, List<ErpEmployeeEntry> records) {
        ErpDirectoryPage page = new ErpDirectoryPage();
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    /**
     * 构造员工条目。
     *
     * @param employeeNo 员工编号
     * @return 条目
     */
    private ErpEmployeeEntry entry(String employeeNo) {
        ErpEmployeeEntry entry = new ErpEmployeeEntry();
        entry.setEmployeeNo(employeeNo);
        entry.setName("员工" + employeeNo);
        entry.setStatus("ACTIVE");
        entry.setOrgCode("ORG01");
        return entry;
    }

    /**
     * 构造旧快照。
     *
     * @param id 主键
     * @return 快照
     */
    private EmployeeSnapshot existing(Long id) {
        EmployeeSnapshot snapshot = new EmployeeSnapshot();
        snapshot.setId(id);
        snapshot.setEmployeeNo("E001");
        return snapshot;
    }
}
