package com.bluewhale.pdm.process.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.mapper.PdmProductMapper;
import com.bluewhale.pdm.process.client.PdmPrcMesClient;
import com.bluewhale.pdm.process.dto.PdmPrcRouteSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcSopSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcStepSaveRequest;
import com.bluewhale.pdm.process.entity.PdmPrcPublish;
import com.bluewhale.pdm.process.entity.PdmPrcRoute;
import com.bluewhale.pdm.process.entity.PdmPrcSop;
import com.bluewhale.pdm.process.entity.PdmPrcStep;
import com.bluewhale.pdm.process.mapper.PdmPrcPublishMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcRouteMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcSopMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcStepMapper;
import com.bluewhale.pdm.process.mapper.PdmPrcToolingMapper;
import com.bluewhale.pdm.process.support.PdmPrcPublishStatus;
import com.bluewhale.pdm.process.support.PdmPrcStatus;
import com.bluewhale.pdm.process.vo.PdmPrcRouteSnapshotVO;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工艺路线服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmPrcRouteServiceImplTest {

    @Mock
    private PdmPrcRouteMapper routeMapper;

    @Mock
    private PdmPrcStepMapper stepMapper;

    @Mock
    private PdmPrcToolingMapper toolingMapper;

    @Mock
    private PdmPrcSopMapper sopMapper;

    @Mock
    private PdmPrcPublishMapper publishMapper;

    @Mock
    private PdmProductMapper productMapper;

    @Mock
    private PdmDocDocumentMapper docMapper;

    @Mock
    private PdmDocVersionMapper docVersionMapper;

    @Mock
    private PdmPrcMesClient mesClient;

    @InjectMocks
    private PdmPrcRouteServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmPrcRoute.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmPrcStep.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmPrcSop.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmPrcPublish.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmDocVersion.class);
        TenantContext.set(1L);
    }

    @AfterAll
    static void clearTenant() {
        TenantContext.set(null);
    }

    @Test
    void createInitialVersionAndRouteNo() {
        PdmProduct part = part(11L, "P-001");
        when(productMapper.selectById(11L)).thenReturn(part);
        when(routeMapper.selectList(any())).thenReturn(List.of());
        when(routeMapper.selectCount(any())).thenReturn(0L);
        stubEmptyDetail();
        doAnswer(invocation -> {
            PdmPrcRoute route = invocation.getArgument(0);
            route.setId(100L);
            return 1;
        }).when(routeMapper).insert(any(PdmPrcRoute.class));

        PdmPrcRouteSaveRequest request = new PdmPrcRouteSaveRequest();
        request.setPartId(11L);

        PdmPrcRouteVO vo = service.create(request, 9L);

        assertEquals("RT-P-001", vo.getRouteNo());
        assertEquals("V1.0", vo.getVersionNo());
        assertEquals(PdmPrcStatus.DRAFT, vo.getStatus());
        assertEquals(9L, vo.getCreatedBy());
        verify(routeMapper).insert(any(PdmPrcRoute.class));
    }

    @Test
    void createNextMajorVersionForSamePart() {
        PdmProduct part = part(11L, "P-001");
        when(productMapper.selectById(11L)).thenReturn(part);
        PdmPrcRoute existing = route(1L, 11L, "V1.0", PdmPrcStatus.RELEASED);
        when(routeMapper.selectList(any())).thenReturn(List.of(existing));
        when(routeMapper.selectCount(any())).thenReturn(0L);
        stubEmptyDetail();
        doAnswer(invocation -> {
            PdmPrcRoute route = invocation.getArgument(0);
            route.setId(101L);
            return 1;
        }).when(routeMapper).insert(any(PdmPrcRoute.class));

        PdmPrcRouteSaveRequest request = new PdmPrcRouteSaveRequest();
        request.setPartId(11L);

        PdmPrcRouteVO vo = service.create(request, 9L);

        assertEquals("V2.0", vo.getVersionNo());
    }

    @Test
    void addStepRejectsNegativeHours() {
        when(routeMapper.selectById(5L)).thenReturn(route(5L, 11L, "V1.0", PdmPrcStatus.DRAFT));
        PdmPrcStepSaveRequest request = stepRequest(1);
        request.setStandardHours(new BigDecimal("-1"));

        BizException ex = assertThrows(BizException.class, () -> service.addStep(5L, request));

        assertEquals("标准工时不能为负", ex.getMessage());
    }

    @Test
    void addStepRejectsDuplicateStepNo() {
        when(routeMapper.selectById(5L)).thenReturn(route(5L, 11L, "V1.0", PdmPrcStatus.DRAFT));
        when(stepMapper.selectCount(any())).thenReturn(1L);
        PdmPrcStepSaveRequest request = stepRequest(1);

        BizException ex = assertThrows(BizException.class, () -> service.addStep(5L, request));

        assertEquals("工序序号已存在", ex.getMessage());
        verify(stepMapper, never()).insert(any(PdmPrcStep.class));
    }

    @Test
    void saveSopRejectsDraftDocumentVersion() {
        when(routeMapper.selectById(5L)).thenReturn(route(5L, 11L, "V1.0", PdmPrcStatus.DRAFT));
        when(stepMapper.selectById(20L)).thenReturn(step(20L, 5L, 1));
        PdmDocVersion draft = new PdmDocVersion();
        draft.setDocumentId(7L);
        draft.setVersionNo("V1.0");
        draft.setStatus("DRAFT");
        when(docVersionMapper.selectList(any())).thenReturn(List.of(draft));
        PdmPrcSopSaveRequest request = new PdmPrcSopSaveRequest();
        request.setStepId(20L);
        request.setDocumentId(7L);
        request.setDocumentVersionNo("V1.0");

        BizException ex = assertThrows(BizException.class, () -> service.saveSop(5L, request));

        assertEquals("SOP 只能引用已发布文档版本", ex.getMessage());
        verify(sopMapper, never()).insert(any(PdmPrcSop.class));
    }

    @Test
    void releaseKeepsReleasedWhenMesUnavailable() {
        PdmPrcRoute route = route(5L, 11L, "V1.0", PdmPrcStatus.DRAFT);
        when(routeMapper.selectById(5L)).thenReturn(route);
        when(publishMapper.selectList(any())).thenReturn(List.of());
        when(stepMapper.selectList(any())).thenReturn(List.of(step(20L, 5L, 1), step(21L, 5L, 2)));
        lenient().when(routeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(toolingMapper.selectList(any())).thenReturn(List.of());
        lenient().when(sopMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            PdmPrcPublish publish = invocation.getArgument(0);
            publish.setId(90L);
            return 1;
        }).when(publishMapper).insert(any(PdmPrcPublish.class));
        when(mesClient.push(any(), any())).thenThrow(new BizException(
                com.bluewhale.common.core.exception.ErrorCode.SYSTEM_ERROR, "MES 服务不可用"));

        service.release(5L, 9L);

        assertEquals(PdmPrcStatus.RELEASED, route.getStatus());
        ArgumentCaptor<PdmPrcPublish> captor = ArgumentCaptor.forClass(PdmPrcPublish.class);
        verify(publishMapper).updateById(captor.capture());
        assertEquals(PdmPrcPublishStatus.FAILED, captor.getValue().getStatus());
        assertEquals("MES 服务不可用", captor.getValue().getLastError());
        assertEquals("RT-P-001:V1.0", captor.getValue().getIdempotencyKey());
    }

    @Test
    void retryPublishMarksSuccess() {
        PdmPrcRoute route = route(5L, 11L, "V1.0", PdmPrcStatus.RELEASED);
        PdmPrcPublish publish = publish(90L, 5L, PdmPrcPublishStatus.FAILED, 0);
        when(routeMapper.selectById(5L)).thenReturn(route);
        when(publishMapper.selectList(any())).thenReturn(List.of(publish));
        when(stepMapper.selectList(any())).thenReturn(List.of(step(20L, 5L, 1)));
        lenient().when(toolingMapper.selectList(any())).thenReturn(List.of());
        lenient().when(sopMapper.selectList(any())).thenReturn(List.of());
        when(mesClient.push(any(), any())).thenReturn("mes-90");

        service.retryPublish(5L);

        ArgumentCaptor<PdmPrcPublish> captor = ArgumentCaptor.forClass(PdmPrcPublish.class);
        verify(publishMapper).updateById(captor.capture());
        assertEquals(PdmPrcPublishStatus.SUCCESS, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        assertEquals("mes-90", captor.getValue().getMesRouteNo());
        assertNotNull(captor.getValue().getPushedAt());
        assertEquals(PdmPrcPublishStatus.SUCCESS, publish.getStatus());
    }

    @Test
    void withdrawMarksObsoleteAndWithdrawn() {
        PdmPrcRoute route = route(5L, 11L, "V1.0", PdmPrcStatus.RELEASED);
        PdmPrcPublish publish = publish(90L, 5L, PdmPrcPublishStatus.SUCCESS, 0);
        when(routeMapper.selectById(5L)).thenReturn(route);
        when(publishMapper.selectList(any())).thenReturn(List.of(publish));
        lenient().when(stepMapper.selectList(any())).thenReturn(List.of());
        lenient().when(toolingMapper.selectList(any())).thenReturn(List.of());
        lenient().when(sopMapper.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new BizException(
                        com.bluewhale.common.core.exception.ErrorCode.SYSTEM_ERROR, "MES 撤回失败"))
                .when(mesClient).withdraw(1L, "RT-P-001", "V1.0");

        service.withdraw(5L);

        assertEquals(PdmPrcStatus.OBSOLETE, route.getStatus());
        assertEquals(PdmPrcPublishStatus.WITHDRAWN, publish.getStatus());
        assertTrue(publish.getWithdrawnAt() != null);
        assertEquals("MES 撤回失败", publish.getLastError());
    }

    @Test
    void updateAfterReleaseIsRejected() {
        when(routeMapper.selectById(5L)).thenReturn(route(5L, 11L, "V1.0", PdmPrcStatus.RELEASED));
        PdmPrcRouteSaveRequest request = new PdmPrcRouteSaveRequest();
        request.setRemark("改备注");

        BizException ex = assertThrows(BizException.class, () -> service.update(5L, request));

        assertEquals("工艺路线已发布，不能再修改", ex.getMessage());
        verify(routeMapper, never()).updateById(any(PdmPrcRoute.class));
    }

    @Test
    void latestReleasedReturnsSnapshot() {
        when(routeMapper.selectList(any()))
                .thenReturn(List.of(route(5L, 11L, "V1.0", PdmPrcStatus.RELEASED)));
        when(stepMapper.selectList(any())).thenReturn(List.of(step(1L, 5L, 10)));

        PdmPrcRouteSnapshotVO snapshot = service.latestReleased("P-001");

        assertEquals("RT-P-001", snapshot.getRouteNo());
        assertEquals("V1.0", snapshot.getVersionNo());
        assertEquals("P-001", snapshot.getPartCode());
        assertEquals(1, snapshot.getSteps().size());
        assertEquals(10, snapshot.getSteps().get(0).getStepNo());
        assertEquals(new BigDecimal("1"), snapshot.getSteps().get(0).getStandardHours());
    }

    @Test
    void latestReleasedRejectsWhenNoReleasedRoute() {
        when(routeMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BizException.class, () -> service.latestReleased("P-001"));
    }

    @Test
    void snapshotRejectsUnreleasedRoute() {
        when(routeMapper.selectList(any()))
                .thenReturn(List.of(route(5L, 11L, "V1.0", PdmPrcStatus.DRAFT)));

        assertThrows(BizException.class, () -> service.snapshot("RT-P-001", "V1.0"));
    }

    @Test
    void snapshotReturnsPinnedVersion() {
        when(routeMapper.selectList(any()))
                .thenReturn(List.of(route(5L, 11L, "V2.0", PdmPrcStatus.RELEASED)));
        when(stepMapper.selectList(any()))
                .thenReturn(List.of(step(1L, 5L, 10), step(2L, 5L, 20)));

        PdmPrcRouteSnapshotVO snapshot = service.snapshot("RT-P-001", "V2.0");

        assertEquals("V2.0", snapshot.getVersionNo());
        assertEquals(2, snapshot.getSteps().size());
    }

    /**
     * 详情装配的空数据桩
     */
    private void stubEmptyDetail() {
        lenient().when(stepMapper.selectList(any())).thenReturn(List.of());
        lenient().when(toolingMapper.selectList(any())).thenReturn(List.of());
        lenient().when(sopMapper.selectList(any())).thenReturn(List.of());
        lenient().when(publishMapper.selectList(any())).thenReturn(List.of());
    }

    /**
     * 构造零件
     *
     * @param id   零件ID
     * @param code 零件编码
     * @return 零件
     */
    private PdmProduct part(Long id, String code) {
        PdmProduct part = new PdmProduct();
        part.setId(id);
        part.setCode(code);
        part.setName("测试零件");
        part.setStatus("ENABLED");
        return part;
    }

    /**
     * 构造路线
     *
     * @param id      路线ID
     * @param partId  零件ID
     * @param version 版本号
     * @param status  状态
     * @return 路线
     */
    private PdmPrcRoute route(Long id, Long partId, String version, String status) {
        PdmPrcRoute route = new PdmPrcRoute();
        route.setId(id);
        route.setRouteNo("RT-P-001");
        route.setPartId(partId);
        route.setPartCode("P-001");
        route.setPartName("测试零件");
        route.setVersionNo(version);
        route.setVersionSeq(10000);
        route.setStatus(status);
        return route;
    }

    /**
     * 构造工序
     *
     * @param id      工序ID
     * @param routeId 路线ID
     * @param stepNo  序号
     * @return 工序
     */
    private PdmPrcStep step(Long id, Long routeId, int stepNo) {
        PdmPrcStep step = new PdmPrcStep();
        step.setId(id);
        step.setRouteId(routeId);
        step.setStepNo(stepNo);
        step.setName("工序" + stepNo);
        step.setStandardHours(BigDecimal.ONE);
        step.setSetupHours(BigDecimal.ZERO);
        return step;
    }

    /**
     * 构造工序请求
     *
     * @param stepNo 序号
     * @return 请求
     */
    private PdmPrcStepSaveRequest stepRequest(int stepNo) {
        PdmPrcStepSaveRequest request = new PdmPrcStepSaveRequest();
        request.setStepNo(stepNo);
        request.setName("车削");
        request.setStandardHours(BigDecimal.ONE);
        request.setSetupHours(BigDecimal.ZERO);
        return request;
    }

    /**
     * 构造下发记录
     *
     * @param id      记录ID
     * @param routeId 路线ID
     * @param status  状态
     * @param retries 重试次数
     * @return 下发记录
     */
    private PdmPrcPublish publish(Long id, Long routeId, String status, int retries) {
        PdmPrcPublish publish = new PdmPrcPublish();
        publish.setId(id);
        publish.setRouteId(routeId);
        publish.setRouteNo("RT-P-001");
        publish.setVersionNo("V1.0");
        publish.setIdempotencyKey("RT-P-001:V1.0");
        publish.setStatus(status);
        publish.setRetryCount(retries);
        return publish;
    }
}
