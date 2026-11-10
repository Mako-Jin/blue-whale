package com.bluewhale.pdm.integration.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.integration.entity.PdmIntReconcile;
import com.bluewhale.pdm.integration.entity.PdmIntSyncLog;
import com.bluewhale.pdm.integration.mapper.PdmIntReconcileMapper;
import com.bluewhale.pdm.integration.mapper.PdmIntSyncLogMapper;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntReconcileStatus;
import com.bluewhale.pdm.integration.support.PdmIntSyncStatus;
import com.bluewhale.pdm.integration.vo.PdmIntReconcileVO;
import com.bluewhale.pdm.part.entity.PdmBomRelease;
import com.bluewhale.pdm.part.mapper.PdmBomReleaseMapper;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.process.entity.PdmPrcPublish;
import com.bluewhale.pdm.process.mapper.PdmPrcPublishMapper;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 集成对账服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmIntReconcileServiceImplTest {

    @Mock
    private PdmIntReconcileMapper reconcileMapper;

    @Mock
    private PdmIntSyncLogMapper syncLogMapper;

    @Mock
    private PdmIntSyncService syncService;

    @Mock
    private PdmBomService bomService;

    @Mock
    private PdmBomReleaseMapper bomReleaseMapper;

    @Mock
    private PdmPrcPublishMapper publishMapper;

    @Mock
    private PdmPrcRouteService routeService;

    @Mock
    private PdmChgRequestMapper requestMapper;

    @Mock
    private PdmChgOrderMapper orderMapper;

    private PdmIntReconcileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PdmIntReconcileServiceImpl(reconcileMapper, syncLogMapper, syncService, bomService,
                bomReleaseMapper, publishMapper, routeService, requestMapper, orderMapper);
    }

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmIntReconcile.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmIntSyncLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmBomRelease.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmPrcPublish.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmChgRequest.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmChgOrder.class);
        TenantContext.set(1L);
    }

    @AfterAll
    static void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void reconcileBomMarksMissingWithoutPullLog() {
        when(bomReleaseMapper.selectList(any())).thenReturn(List.of(release("BOM-001", "V1.0", "abc")));
        when(syncLogMapper.selectOne(any())).thenReturn(null);
        when(reconcileMapper.selectOne(any())).thenReturn(null);

        List<PdmIntReconcileVO> results = service.reconcileBom();

        assertEquals(1, results.size());
        assertEquals(PdmIntReconcileStatus.MISSING, results.get(0).getMatchStatus());
        verify(reconcileMapper).insert(any(PdmIntReconcile.class));
    }

    @Test
    void reconcileBomMarksMatchWhenChecksumEqual() {
        when(bomReleaseMapper.selectList(any())).thenReturn(List.of(release("BOM-001", "V1.0", "abc")));
        when(syncLogMapper.selectOne(any())).thenReturn(pullLog("abc"));
        when(reconcileMapper.selectOne(any())).thenReturn(null);

        List<PdmIntReconcileVO> results = service.reconcileBom();

        assertEquals(PdmIntReconcileStatus.MATCH, results.get(0).getMatchStatus());
    }

    @Test
    void reconcileBomMarksDiffWhenChecksumMismatch() {
        when(bomReleaseMapper.selectList(any())).thenReturn(List.of(release("BOM-001", "V1.0", "abc")));
        when(syncLogMapper.selectOne(any())).thenReturn(pullLog("xyz"));
        when(reconcileMapper.selectOne(any())).thenReturn(null);

        List<PdmIntReconcileVO> results = service.reconcileBom();

        assertEquals(PdmIntReconcileStatus.DIFF, results.get(0).getMatchStatus());
        assertEquals("xyz", results.get(0).getRemoteChecksum());
    }

    @Test
    void reconcileOaDetectsStatusMismatch() {
        PdmChgRequest request = new PdmChgRequest();
        request.setChangeNo("ECR-001");
        request.setApprovalInstanceId(100L);
        request.setStatus(PdmChgRequestStatus.SUBMITTED);
        request.setApprovalStatus("APPROVED");
        when(requestMapper.selectList(any())).thenReturn(List.of(request));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(reconcileMapper.selectOne(any())).thenReturn(null);

        List<PdmIntReconcileVO> results = service.reconcileOa();

        assertEquals(1, results.size());
        assertEquals(PdmIntReconcileStatus.DIFF, results.get(0).getMatchStatus());
        assertEquals("APPROVED", results.get(0).getRemoteStatus());
    }

    @Test
    void compensateOaRejectsApprovedRequest() {
        PdmChgRequest request = new PdmChgRequest();
        request.setStatus(PdmChgRequestStatus.APPROVED);
        when(requestMapper.selectById(9L)).thenReturn(request);

        assertThrows(BizException.class, () -> service.compensateOa("REQUEST", 9L, 99L));
    }

    @Test
    void compensateOaAppliesApprovedStatus() {
        PdmChgRequest request = new PdmChgRequest();
        request.setId(9L);
        request.setChangeNo("ECR-001");
        request.setStatus(PdmChgRequestStatus.SUBMITTED);
        request.setApprovalStatus("APPROVED");
        when(requestMapper.selectById(9L)).thenReturn(request);
        when(reconcileMapper.selectOne(any())).thenReturn(null);

        PdmIntReconcileVO result = service.compensateOa("REQUEST", 9L, 99L);

        assertEquals(PdmChgRequestStatus.APPROVED, request.getStatus());
        assertEquals(PdmIntReconcileStatus.MATCH, result.getMatchStatus());
    }

    private PdmBomRelease release(String bomNo, String versionNo, String checksum) {
        PdmBomRelease release = new PdmBomRelease();
        release.setBomNo(bomNo);
        release.setVersionNo(versionNo);
        release.setChecksum(checksum);
        return release;
    }

    private PdmIntSyncLog pullLog(String checksum) {
        PdmIntSyncLog log = new PdmIntSyncLog();
        log.setChecksum(checksum);
        log.setStatus(PdmIntSyncStatus.SUCCESS);
        return log;
    }
}
