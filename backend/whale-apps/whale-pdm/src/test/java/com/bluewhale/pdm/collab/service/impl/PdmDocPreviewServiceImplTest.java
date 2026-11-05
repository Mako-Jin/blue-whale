package com.bluewhale.pdm.collab.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.collab.entity.PdmDocPreviewJob;
import com.bluewhale.pdm.collab.mapper.PdmDocPreviewJobMapper;
import com.bluewhale.pdm.collab.support.PdmDocPreviewStatus;
import com.bluewhale.pdm.collab.vo.PdmDocPreviewJobVO;
import com.bluewhale.pdm.document.client.PdmDocFileClient;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 在线预览服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmDocPreviewServiceImplTest {

    @Mock
    private PdmDocPreviewJobMapper jobMapper;

    @Mock
    private PdmDocDocumentService documentService;

    @Mock
    private PdmDocFileClient fileClient;

    @InjectMocks
    private PdmDocPreviewServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmDocPreviewJob.class);
        TenantContext.set(1L);
    }

    @AfterAll
    static void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void requestPdfMarksSuccessWithOriginalFormat() {
        lenient().when(documentService.versions(any(), any())).thenReturn(List.of(version("图纸.pdf",
                "application/pdf")));
        lenient().when(jobMapper.selectOne(any())).thenReturn(null);

        PdmDocPreviewJobVO vo = service.request(10L, 100L, "2D", viewer());

        assertEquals(PdmDocPreviewStatus.SUCCESS, vo.getStatus());
        assertEquals("PDF", vo.getViewerHint());
        assertEquals("ORIGINAL", vo.getTargetFormat());
    }

    @Test
    void requestStepMarksUnsupportedWithReason() {
        lenient().when(documentService.versions(any(), any())).thenReturn(List.of(version("壳体.step",
                "application/octet-stream")));
        lenient().when(jobMapper.selectOne(any())).thenReturn(null);

        PdmDocPreviewJobVO vo = service.request(10L, 100L, "3D", viewer());

        assertEquals(PdmDocPreviewStatus.UNSUPPORTED, vo.getStatus());
        assertNotNull(vo.getLastError());
    }

    @Test
    void requestSameKeyReturnsExistingJob() {
        PdmDocPreviewJob existing = new PdmDocPreviewJob();
        existing.setId(3L);
        existing.setStatus(PdmDocPreviewStatus.SUCCESS);
        existing.setPreviewType("2D");
        lenient().when(documentService.versions(any(), any())).thenReturn(List.of(version("图纸.pdf",
                "application/pdf")));
        lenient().when(jobMapper.selectOne(any())).thenReturn(existing);

        PdmDocPreviewJobVO vo = service.request(10L, 100L, "2D", viewer());

        assertEquals(3L, vo.getId());
    }

    @Test
    void requestRejectsUnknownPreviewType() {
        assertThrows(RuntimeException.class, () -> service.request(10L, 100L, "4D", viewer()));
    }

    @Test
    void retryOnSuccessJobKeepsResult() {
        PdmDocPreviewJob job = new PdmDocPreviewJob();
        job.setId(3L);
        job.setStatus(PdmDocPreviewStatus.SUCCESS);
        when(jobMapper.selectById(3L)).thenReturn(job);

        PdmDocPreviewJobVO vo = service.retry(3L, viewer());

        assertEquals(PdmDocPreviewStatus.SUCCESS, vo.getStatus());
    }

    private PdmDocVersionVO version(String fileName, String contentType) {
        PdmDocVersionVO vo = new PdmDocVersionVO();
        vo.setId(100L);
        vo.setVersionNo("V1.0");
        vo.setFileName(fileName);
        vo.setContentType(contentType);
        return vo;
    }

    private PdmDocViewer viewer() {
        return new PdmDocViewer(88L, Set.of("pdm_member"));
    }
}
