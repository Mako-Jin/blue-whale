package com.bluewhale.pdm.integration.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.integration.client.PdmIntErpClient;
import com.bluewhale.pdm.integration.client.PdmIntErpItemResult;
import com.bluewhale.pdm.integration.entity.PdmIntSyncLog;
import com.bluewhale.pdm.integration.mapper.PdmIntSyncLogMapper;
import com.bluewhale.pdm.integration.support.PdmIntAction;
import com.bluewhale.pdm.integration.support.PdmIntSyncStatus;
import com.bluewhale.pdm.integration.vo.PdmIntErpPartResultVO;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 集成同步服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmIntSyncServiceImplTest {

    @Mock
    private PdmIntSyncLogMapper syncLogMapper;

    @Mock
    private PdmIntErpClient erpClient;

    @Mock
    private PdmProductService productService;

    @Mock
    private PdmPrcRouteService routeService;

    private PdmIntSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PdmIntSyncServiceImpl(syncLogMapper, erpClient, productService, routeService,
                new ObjectMapper());
    }

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmIntSyncLog.class);
        TenantContext.set(1L);
    }

    @AfterAll
    static void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void syncPartToErpWritesSuccessLog() {
        when(productService.getDetail(7L)).thenReturn(part());
        when(erpClient.upsertItem(any(), any())).thenReturn(new PdmIntErpItemResult(88L, "P-001", true));
        when(syncLogMapper.selectOne(any())).thenReturn(null);

        PdmIntErpPartResultVO result = service.syncPartToErp(7L, 99L);

        assertEquals(PdmIntSyncStatus.SUCCESS, result.getStatus());
        assertEquals("P-001", result.getCode());
        assertEquals(88L, result.getErpItemId());
        assertTrue(result.isCreated());
        ArgumentCaptor<PdmIntSyncLog> captor = ArgumentCaptor.forClass(PdmIntSyncLog.class);
        verify(syncLogMapper).insert(captor.capture());
        assertEquals("ERP:PART:P-001", captor.getValue().getIdempotencyKey());
        assertEquals(PdmIntSyncStatus.SUCCESS, captor.getValue().getStatus());
    }

    @Test
    void syncPartToErpRecordsFailure() {
        when(productService.getDetail(7L)).thenReturn(part());
        when(erpClient.upsertItem(any(), any())).thenThrow(new BizException(
                com.bluewhale.common.core.exception.ErrorCode.SYSTEM_ERROR, "ERP 服务不可用"));
        when(syncLogMapper.selectOne(any())).thenReturn(null);

        PdmIntErpPartResultVO result = service.syncPartToErp(7L, 99L);

        assertEquals(PdmIntSyncStatus.FAILED, result.getStatus());
        assertEquals("ERP 服务不可用", result.getLastError());
        ArgumentCaptor<PdmIntSyncLog> captor = ArgumentCaptor.forClass(PdmIntSyncLog.class);
        verify(syncLogMapper).insert(captor.capture());
        assertEquals(PdmIntSyncStatus.FAILED, captor.getValue().getStatus());
        assertEquals("ERP 服务不可用", captor.getValue().getLastError());
    }

    @Test
    void recordBomPullUsesVersionScopedKey() {
        when(syncLogMapper.selectOne(any())).thenReturn(null);

        service.recordBomPull("BOM-001", "V1.0", "abc123");

        ArgumentCaptor<PdmIntSyncLog> captor = ArgumentCaptor.forClass(PdmIntSyncLog.class);
        verify(syncLogMapper).insert(captor.capture());
        assertEquals("ERP:BOM_PULL:BOM-001:V1.0", captor.getValue().getIdempotencyKey());
        assertEquals("abc123", captor.getValue().getChecksum());
        assertEquals(PdmIntSyncStatus.SUCCESS, captor.getValue().getStatus());
    }

    @Test
    void retryRejectsUnsupportedAction() {
        PdmIntSyncLog log = new PdmIntSyncLog();
        log.setId(1L);
        log.setAction(PdmIntAction.CAD_CHECKIN);
        when(syncLogMapper.selectById(1L)).thenReturn(log);

        assertThrows(BizException.class, () -> service.retry(1L, 99L));
    }

    @Test
    void retryRoutePushRetriesPublish() {
        PdmIntSyncLog log = new PdmIntSyncLog();
        log.setId(2L);
        log.setAction(PdmIntAction.ROUTE_PUSH);
        log.setBizId(30L);
        log.setStatus(PdmIntSyncStatus.FAILED);
        log.setRetryCount(1);
        when(syncLogMapper.selectById(2L)).thenReturn(log);

        service.retry(2L, 99L);

        verify(routeService).retryPublish(30L);
        assertEquals(PdmIntSyncStatus.SUCCESS, log.getStatus());
        assertEquals(2, log.getRetryCount());
    }

    private PdmProductVO part() {
        PdmProductVO part = new PdmProductVO();
        part.setId(7L);
        part.setCode("P-001");
        part.setName("壳体");
        part.setStatus("ENABLED");
        part.setMaterial("AL6061");
        part.setSpec("100x50");
        part.setUom("件");
        part.setPartType("PART");
        return part;
    }
}
