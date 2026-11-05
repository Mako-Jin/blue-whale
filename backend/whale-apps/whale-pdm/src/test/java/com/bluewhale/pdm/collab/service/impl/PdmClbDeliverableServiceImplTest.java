package com.bluewhale.pdm.collab.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableSaveRequest;
import com.bluewhale.pdm.collab.entity.PdmClbDeliverable;
import com.bluewhale.pdm.collab.mapper.PdmClbDeliverableMapper;
import com.bluewhale.pdm.collab.vo.PdmClbDeliverableVO;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.bluewhale.pdm.process.entity.PdmPrcRoute;
import com.bluewhale.pdm.process.mapper.PdmPrcRouteMapper;
import com.bluewhale.pdm.process.support.PdmPrcStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 项目交付物服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmClbDeliverableServiceImplTest {

    @Mock
    private PdmClbDeliverableMapper deliverableMapper;

    @Mock
    private PdmDocDocumentMapper documentMapper;

    @Mock
    private PdmDocVersionMapper versionMapper;

    @Mock
    private PdmBomMapper bomMapper;

    @Mock
    private PdmPrcRouteMapper routeMapper;

    @InjectMocks
    private PdmClbDeliverableServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmClbDeliverable.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmDocVersion.class);
        TenantContext.set(1L);
    }

    @AfterAll
    static void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void archiveReleasedDocumentVersionFillsSnapshot() {
        PdmDocDocument document = new PdmDocDocument();
        document.setDocNo("DOC-001");
        document.setTitle("总装图纸");
        PdmDocVersion version = new PdmDocVersion();
        version.setDocumentId(10L);
        version.setVersionNo("V1.0");
        version.setStatus("RELEASED");
        lenient().when(documentMapper.selectById(10L)).thenReturn(document);
        lenient().when(versionMapper.selectOne(any())).thenReturn(version);
        lenient().when(deliverableMapper.selectCount(any())).thenReturn(0L);

        PdmClbDeliverableVO vo = service.archive(documentRequest(10L, "V1.0"), 99L);

        assertEquals("DOC-001", vo.getObjectNo());
        assertEquals("总装图纸", vo.getObjectTitle());
        assertEquals("ARCHIVED", vo.getStatus());
        assertEquals(99L, vo.getArchivedBy());
    }

    @Test
    void archiveRejectsDraftDocumentVersion() {
        PdmDocDocument document = new PdmDocDocument();
        PdmDocVersion version = new PdmDocVersion();
        version.setDocumentId(10L);
        version.setVersionNo("V0.1");
        version.setStatus("DRAFT");
        lenient().when(documentMapper.selectById(10L)).thenReturn(document);
        lenient().when(versionMapper.selectOne(any())).thenReturn(version);

        assertThrows(BizException.class, () -> service.archive(documentRequest(10L, "V0.1"), 99L));
    }

    @Test
    void archiveRejectsUnreleasedBom() {
        PdmBom bom = new PdmBom();
        bom.setBomNo("BOM-001");
        bom.setVersionNo("V1.0");
        bom.setStatus(PdmPrcStatus.DRAFT);
        lenient().when(bomMapper.selectById(20L)).thenReturn(bom);

        PdmClbDeliverableSaveRequest request = objectRequest("BOM", 20L, "V1.0");
        assertThrows(BizException.class, () -> service.archive(request, 99L));
    }

    @Test
    void archiveRejectsDuplicateDeliverable() {
        PdmDocDocument document = new PdmDocDocument();
        PdmDocVersion version = new PdmDocVersion();
        version.setStatus("RELEASED");
        lenient().when(documentMapper.selectById(10L)).thenReturn(document);
        lenient().when(versionMapper.selectOne(any())).thenReturn(version);
        lenient().when(deliverableMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.archive(documentRequest(10L, "V1.0"), 99L));
    }

    @Test
    void deleteRemovesExistingDeliverable() {
        PdmClbDeliverable entity = new PdmClbDeliverable();
        entity.setId(5L);
        when(deliverableMapper.selectById(5L)).thenReturn(entity);

        assertDoesNotThrow(() -> service.delete(5L));
    }

    private PdmClbDeliverableSaveRequest documentRequest(Long objectId, String versionNo) {
        return objectRequest("DOCUMENT", objectId, versionNo);
    }

    private PdmClbDeliverableSaveRequest objectRequest(String type, Long objectId, String versionNo) {
        PdmClbDeliverableSaveRequest request = new PdmClbDeliverableSaveRequest();
        request.setProjectId(1L);
        request.setProjectName("新产品研发");
        request.setObjectType(type);
        request.setObjectId(objectId);
        request.setObjectVersionNo(versionNo);
        return request;
    }
}
