package com.bluewhale.lms.integration.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.view.MemberSyncResultView;
import com.bluewhale.lms.integration.domain.gateway.ErpEmployeeGateway;
import com.bluewhale.lms.integration.domain.model.ErpDirectoryPage;
import com.bluewhale.lms.integration.domain.model.ErpEmployeeEntry;
import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.model.MemberStatus;
import com.bluewhale.lms.integration.domain.repository.MemberSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工快照同步：分页拉取、逐条 upsert、脏行跳过、源故障 fail-fast。
 *
 * @author BlueWhale
 */
class MemberSnapshotAppServiceImplTest {

    /** 员工编号 */
    private static final String EMP_NO = "E001";

    private MemberSnapshotRepository memberSnapshotRepository;

    private ErpEmployeeGateway erpEmployeeGateway;

    private MemberSnapshotAppServiceImpl service;

    private LmsActor admin;

    @BeforeEach
    void setUp() {
        memberSnapshotRepository = mock(MemberSnapshotRepository.class);
        erpEmployeeGateway = mock(ErpEmployeeGateway.class);
        LmsProperties properties = new LmsProperties();
        properties.setDemandGatePassed(true);
        properties.setMemberSyncPageSize(100);
        properties.setMemberPageLimit(100);
        service = new MemberSnapshotAppServiceImpl(memberSnapshotRepository, erpEmployeeGateway, properties);
        admin = new LmsActor(1L, "admin", Set.of("tenant_admin"), Set.of());
    }

    /**
     * 单页三类结果分别计数。
     */
    @Test
    @DisplayName("同步分别统计新增、更新与跳过")
    void syncCountsOutcomes() {
        ErpEmployeeEntry fresh = entry("E001", 1L);
        ErpEmployeeEntry existing = entry("E002", 2L);
        ErpEmployeeEntry broken = entry(" ", null);
        when(erpEmployeeGateway.fetchPage(1L, 100L)).thenReturn(page(3L, fresh, existing, broken));

        MemberSnapshot found = new MemberSnapshot();
        found.setId(88L);
        when(memberSnapshotRepository.findByEmployeeNo("E001")).thenReturn(Optional.empty());
        when(memberSnapshotRepository.findByEmployeeNo("E002")).thenReturn(Optional.of(found));
        when(memberSnapshotRepository.save(any(MemberSnapshot.class)))
                .thenAnswer(invocation -> {
                    MemberSnapshot saved = invocation.getArgument(0);
                    return saved.getId() == null ? 1000L : saved.getId();
                });
        when(memberSnapshotRepository.countAll()).thenReturn(2L);

        MemberSyncResultView result = service.sync(admin);

        assertEquals(1, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getSkipped());
        assertEquals(3L, result.getSourceTotal());
        assertEquals(2L, result.getSnapshotTotal());
    }

    /**
     * 已存在的员工复用主键，不会插出一条重复记录。
     */
    @Test
    @DisplayName("已存在的员工复用主键")
    void existingMemberReusesId() {
        when(erpEmployeeGateway.fetchPage(1L, 100L)).thenReturn(page(1L, entry(EMP_NO, 1L)));
        MemberSnapshot found = new MemberSnapshot();
        found.setId(77L);
        when(memberSnapshotRepository.findByEmployeeNo(EMP_NO)).thenReturn(Optional.of(found));
        when(memberSnapshotRepository.save(any(MemberSnapshot.class))).thenReturn(77L);
        when(memberSnapshotRepository.countAll()).thenReturn(1L);

        service.sync(admin);

        ArgumentCaptor<MemberSnapshot> captor = ArgumentCaptor.forClass(MemberSnapshot.class);
        verify(memberSnapshotRepository).save(captor.capture());
        assertEquals(77L, captor.getValue().getId());
    }

    /**
     * ERP 不可达时上抛，不能当成空目录把全员置为离职。
     */
    @Test
    @DisplayName("源故障上抛而不是降级为空目录")
    void syncFailsFastWhenErpUnavailable() {
        when(erpEmployeeGateway.fetchPage(anyLong(), anyLong()))
                .thenThrow(LmsErrorCode.exception(LmsErrorCode.MEMBER_SYNC_FAILED, "ERP 不可用"));

        BizException exception = assertThrows(BizException.class, () -> service.sync(admin));
        assertEquals(LmsErrorCode.MEMBER_SYNC_FAILED, exception.getCode());
        verify(memberSnapshotRepository, never()).save(any(MemberSnapshot.class));
    }

    /**
     * 非管理员不可同步。
     */
    @Test
    @DisplayName("非管理员不可同步")
    void syncRejectsNonAdmin() {
        LmsActor member = new LmsActor(9L, "member", Set.of("lms_member"), Set.of());
        BizException exception = assertThrows(BizException.class, () -> service.sync(member));
        assertEquals(LmsErrorCode.MEMBER_INVALID, exception.getCode());
        verify(erpEmployeeGateway, never()).fetchPage(anyLong(), anyLong());
    }

    /**
     * 空页立即收敛，不因 total 虚高而空转。
     */
    @Test
    @DisplayName("空页立即结束翻页")
    void emptyPageStopsLoop() {
        ErpDirectoryPage first = new ErpDirectoryPage();
        first.setTotal(500L);
        first.setRecords(List.of(entry("E100", 100L)));
        ErpDirectoryPage empty = new ErpDirectoryPage();
        empty.setTotal(500L);
        empty.setRecords(List.of());
        when(erpEmployeeGateway.fetchPage(1L, 100L)).thenReturn(first);
        when(erpEmployeeGateway.fetchPage(2L, 100L)).thenReturn(empty);
        when(memberSnapshotRepository.findByEmployeeNo("E100")).thenReturn(Optional.empty());
        when(memberSnapshotRepository.save(any(MemberSnapshot.class))).thenReturn(1L);

        MemberSyncResultView result = service.sync(admin);

        assertEquals(1, result.getCreated());
        verify(erpEmployeeGateway, never()).fetchPage(eq(3L), anyLong());
    }

    /**
     * 可达性判断：未收录按不可达处理。
     */
    @Test
    @DisplayName("未收录账号按不可达处理")
    void reachableRequiresSnapshot() {
        when(memberSnapshotRepository.findByUserId(5L)).thenReturn(Optional.empty());
        assertFalse(service.isReachable(5L));

        MemberSnapshot offboard = new MemberSnapshot();
        offboard.setStatus(MemberStatus.LEFT);
        offboard.setUserId(5L);
        when(memberSnapshotRepository.findByUserId(5L)).thenReturn(Optional.of(offboard));
        assertFalse(service.isReachable(5L));

        MemberSnapshot active = new MemberSnapshot();
        active.setStatus(MemberStatus.ACTIVE);
        active.setUserId(5L);
        when(memberSnapshotRepository.findByUserId(5L)).thenReturn(Optional.of(active));
        assertTrue(service.isReachable(5L));
    }

    /**
     * 构造分页结果。
     *
     * @param total 总数
     * @param entries 条目
     * @return 分页
     */
    private ErpDirectoryPage page(long total, ErpEmployeeEntry... entries) {
        ErpDirectoryPage page = new ErpDirectoryPage();
        page.setTotal(total);
        page.setRecords(List.of(entries));
        return page;
    }

    /**
     * 构造目录条目。
     *
     * @param employeeNo 员工编号
     * @param userId 账号 ID
     * @return 条目
     */
    private ErpEmployeeEntry entry(String employeeNo, Long userId) {
        ErpEmployeeEntry entry = new ErpEmployeeEntry();
        entry.setEmployeeNo(employeeNo);
        entry.setUserId(userId);
        entry.setName("张三");
        entry.setStatus("ACTIVE");
        entry.setHireDate(LocalDate.of(2025, 1, 1));
        return entry;
    }
}
