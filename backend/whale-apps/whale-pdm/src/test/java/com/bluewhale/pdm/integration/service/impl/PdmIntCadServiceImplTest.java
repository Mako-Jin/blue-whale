package com.bluewhale.pdm.integration.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import com.bluewhale.pdm.dto.PdmProductQueryRequest;
import com.bluewhale.pdm.integration.dto.PdmIntCadEbomImportRequest;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntSyncStatus;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CAD 集成服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmIntCadServiceImplTest {

    @Mock
    private PdmDocDocumentService documentService;

    @Mock
    private PdmBomService bomService;

    @Mock
    private PdmProductService productService;

    @Mock
    private PdmIntSyncService syncService;

    private PdmIntCadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PdmIntCadServiceImpl(documentService, bomService, productService, syncService,
                new ObjectMapper());
    }

    @Test
    void importEbomReplaysExistingResult() {
        PdmIntSyncLogVO log = new PdmIntSyncLogVO();
        log.setStatus(PdmIntSyncStatus.SUCCESS);
        log.setExternalRef("31");
        when(syncService.findByKey(any(), eq("CAD:EBOM:ASM-1"))).thenReturn(log);
        PdmBomVO bom = new PdmBomVO();
        bom.setId(31L);
        when(bomService.detail(31L)).thenReturn(bom);

        PdmBomVO result = service.importEbom(request(), 99L);

        assertEquals(31L, result.getId());
        verify(bomService, never()).create(any());
    }

    @Test
    void importEbomCreatesBomAndLines() {
        when(syncService.findByKey(any(), eq("CAD:EBOM:ASM-1"))).thenReturn(null);
        when(productService.page(any())).thenAnswer(invocation -> {
            PdmProductQueryRequest query = invocation.getArgument(0);
            PdmProductVO part = part(1L, query.getCode());
            return PageResult.of(List.of(part), 1L, 1L, 20L);
        });
        PdmBomVO created = new PdmBomVO();
        created.setId(30L);
        created.setBomNo("ASM-1-CAD");
        when(bomService.create(any())).thenReturn(created);
        PdmBomVO detail = new PdmBomVO();
        detail.setId(30L);
        detail.setBomNo("ASM-1-CAD");
        detail.setVersionNo("V0.1");
        when(bomService.detail(30L)).thenReturn(detail);

        PdmBomVO result = service.importEbom(request(), 99L);

        assertEquals(30L, result.getId());
        verify(bomService, org.mockito.Mockito.times(2)).addLine(eq(30L), any());
    }

    @Test
    void importEbomRejectsUnknownChild() {
        when(syncService.findByKey(any(), eq("CAD:EBOM:ASM-1"))).thenReturn(null);
        when(productService.page(any())).thenAnswer(invocation -> {
            PdmProductQueryRequest query = invocation.getArgument(0);
            if (!"ASM-1".equals(query.getCode())) {
                return PageResult.of(List.of(), 0L, 1L, 20L);
            }
            return PageResult.of(List.of(part(1L, "ASM-1")), 1L, 1L, 20L);
        });
        PdmBomVO created = new PdmBomVO();
        created.setId(30L);
        created.setBomNo("ASM-1-CAD");
        when(bomService.create(any())).thenReturn(created);

        assertThrows(BizException.class, () -> service.importEbom(request(), 99L));
        verify(bomService).delete(30L);
    }

    @Test
    void checkinReplaysExistingVersion() {
        PdmIntSyncLogVO log = new PdmIntSyncLogVO();
        log.setStatus(PdmIntSyncStatus.SUCCESS);
        log.setExternalRef("12");
        when(syncService.findByKey(any(), eq("CAD:CHECKIN:CAD-1"))).thenReturn(log);
        PdmDocVersionVO version = new PdmDocVersionVO();
        version.setId(12L);
        when(documentService.versions(eq(5L), any())).thenReturn(List.of(version));

        PdmDocVersionVO result = service.checkin(5L, "检入", "CAD-1", null,
                new PdmDocViewer(99L, Set.of()), 99L);

        assertEquals(12L, result.getId());
        verify(documentService, never()).checkIn(anyLong(), any(), any(), any());
    }

    private PdmIntCadEbomImportRequest request() {
        PdmIntCadEbomImportRequest request = new PdmIntCadEbomImportRequest();
        request.setExternalKey("ASM-1");
        request.setPartNo("ASM-1");
        PdmIntCadEbomImportRequest.Line first = new PdmIntCadEbomImportRequest.Line();
        first.setChildPartNo("P-001");
        first.setQty(BigDecimal.ONE);
        PdmIntCadEbomImportRequest.Line second = new PdmIntCadEbomImportRequest.Line();
        second.setChildPartNo("P-002");
        second.setQty(new BigDecimal("2"));
        request.setLines(List.of(first, second));
        return request;
    }

    private PdmProductVO part(Long id, String code) {
        PdmProductVO part = new PdmProductVO();
        part.setId(id);
        part.setCode(code);
        part.setName("零件-" + code);
        part.setUom("件");
        return part;
    }
}
