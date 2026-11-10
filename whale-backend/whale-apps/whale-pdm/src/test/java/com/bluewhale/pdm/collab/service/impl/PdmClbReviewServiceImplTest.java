package com.bluewhale.pdm.collab.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.collab.dto.PdmClbReviewCloseRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewSaveRequest;
import com.bluewhale.pdm.collab.entity.PdmClbReview;
import com.bluewhale.pdm.collab.mapper.PdmClbReviewMapper;
import com.bluewhale.pdm.collab.support.PdmClbReviewConstants;
import com.bluewhale.pdm.collab.vo.PdmClbReviewVO;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * 研发评审服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmClbReviewServiceImplTest {

    @Mock
    private PdmClbReviewMapper reviewMapper;

    @Mock
    private PdmDocDocumentMapper documentMapper;

    @Mock
    private PdmDocVersionMapper versionMapper;

    @InjectMocks
    private PdmClbReviewServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmClbReview.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmDocVersion.class);
        TenantContext.set(1L);
    }

    @AfterAll
    static void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createGeneratesDraftReviewWithNumber() {
        PdmClbReviewVO vo = service.create(saveRequest(), 88L);

        assertTrue(vo.getReviewNo().startsWith("RV-"));
        assertEquals(PdmClbReviewConstants.STATUS_DRAFT, vo.getStatus());
        assertEquals(88L, vo.getCreatedBy());
    }

    @Test
    void closeMarksReviewClosedWithConclusion() {
        PdmClbReview entity = draftReview();
        lenient().when(reviewMapper.selectById(7L)).thenReturn(entity);

        PdmClbReviewCloseRequest request = new PdmClbReviewCloseRequest();
        request.setConclusion("PASSED");
        request.setConclusionComment("同意转产");
        PdmClbReviewVO vo = service.close(7L, request, 88L);

        assertEquals(PdmClbReviewConstants.STATUS_CLOSED, vo.getStatus());
        assertEquals("PASSED", vo.getConclusion());
        assertNotNull(vo.getClosedAt());
    }

    @Test
    void closeRejectsInvalidConclusion() {
        PdmClbReview entity = draftReview();
        lenient().when(reviewMapper.selectById(7L)).thenReturn(entity);

        PdmClbReviewCloseRequest request = new PdmClbReviewCloseRequest();
        request.setConclusion("UNKNOWN");

        assertThrows(BizException.class, () -> service.close(7L, request, 88L));
    }

    @Test
    void updateClosedReviewIsRejected() {
        PdmClbReview entity = draftReview();
        entity.setStatus(PdmClbReviewConstants.STATUS_CLOSED);
        lenient().when(reviewMapper.selectById(7L)).thenReturn(entity);

        assertThrows(BizException.class, () -> service.update(7L, saveRequest(), 88L));
    }

    private PdmClbReview draftReview() {
        PdmClbReview entity = new PdmClbReview();
        entity.setId(7L);
        entity.setReviewNo("RV-20261105-0001");
        entity.setStatus(PdmClbReviewConstants.STATUS_DRAFT);
        return entity;
    }

    private PdmClbReviewSaveRequest saveRequest() {
        PdmClbReviewSaveRequest request = new PdmClbReviewSaveRequest();
        request.setReviewType("DESIGN");
        request.setTitle("结构设计评审");
        request.setProjectId(1L);
        return request;
    }
}
