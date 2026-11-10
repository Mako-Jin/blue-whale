package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pdm.change.dto.PdmChgRequestQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgRequestSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.change.support.PdmChgTraceRecorder;
import com.bluewhale.pdm.change.client.PdmChgWorkflowClient;
import com.bluewhale.pdm.change.vo.PdmChgImpactObjectVO;
import com.bluewhale.pdm.change.vo.PdmChgRequestVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更申请服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmChgRequestServiceImplTest {

    /** 影响对象类型：零件 */
    private static final String PART_TYPE = "PART";

    /** 非法影响对象类型 */
    private static final String UNKNOWN_TYPE = "UNKNOWN";

    @Mock
    private PdmChgRequestMapper chgRequestMapper;

    @Mock
    private PdmChgTraceRecorder traceRecorder;

    @Mock
    private PdmChgWorkflowClient workflowClient;

    @InjectMocks
    private PdmChgRequestServiceImpl chgRequestService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmChgRequest.class);
    }

    /**
     * 新建申请落草稿，影响对象序列化后可回显。
     */
    @Test
    void createShouldSaveDraftWithImpact() {
        when(chgRequestMapper.insert(any(PdmChgRequest.class))).thenReturn(1);

        PdmChgRequestVO vo = chgRequestService.create(saveRequest(PART_TYPE, 1001L, "PART-001"), 9L);

        assertEquals(PdmChgRequestStatus.DRAFT, vo.getStatus());
        assertEquals(9L, vo.getInitiatorId());
        assertEquals("设计变更", vo.getChangeReason());
        assertTrue(vo.getChangeNo().startsWith("CR-"));
        assertEquals(1, vo.getImpactObjects().size());
        assertEquals("PART-001", vo.getImpactObjects().get(0).getObjectCode());
        verify(chgRequestMapper).insert(any(PdmChgRequest.class));
    }

    /**
     * 影响对象为空时不允许提交。
     */
    @Test
    void submitShouldRejectEmptyImpact() {
        PdmChgRequest entity = draft(1L);
        entity.setImpactJson("[]");
        when(chgRequestMapper.selectById(1L)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> chgRequestService.submit(1L));

        assertTrue(ex.getMessage().contains("影响对象不能为空"));
        verify(chgRequestMapper, never()).updateById(any(PdmChgRequest.class));
    }

    /**
     * 草稿提交后状态为已提交并记录提交时间。
     */
    @Test
    void submitShouldMoveToSubmitted() {
        PdmChgRequest entity = draft(1L);
        entity.setImpactJson("[{\"objectType\":\"PART\",\"objectId\":1001}]");
        when(chgRequestMapper.selectById(1L)).thenReturn(entity);
        when(chgRequestMapper.updateById(any(PdmChgRequest.class))).thenReturn(1);

        PdmChgRequestVO vo = chgRequestService.submit(1L);

        assertEquals(PdmChgRequestStatus.SUBMITTED, vo.getStatus());
        assertNotNull(vo.getSubmitTime());
    }

    /**
     * 非草稿不可修改与删除。
     */
    @Test
    void updateShouldRejectNonDraft() {
        PdmChgRequest entity = draft(1L);
        entity.setStatus(PdmChgRequestStatus.SUBMITTED);
        when(chgRequestMapper.selectById(1L)).thenReturn(entity);

        assertThrows(BizException.class, () -> chgRequestService.update(1L, saveRequest(PART_TYPE, 1001L, "P")));
        assertThrows(BizException.class, () -> chgRequestService.delete(1L));
        verify(chgRequestMapper, never()).updateById(any(PdmChgRequest.class));
        verify(chgRequestMapper, never()).deleteById(any(Long.class));
    }

    /**
     * 仅已提交的申请可审批通过。
     */
    @Test
    void approveShouldRequireSubmitted() {
        PdmChgRequest entity = draft(1L);
        when(chgRequestMapper.selectById(1L)).thenReturn(entity);

        assertThrows(BizException.class, () -> chgRequestService.approve(1L, "同意", 10L));

        entity.setStatus(PdmChgRequestStatus.SUBMITTED);
        when(chgRequestMapper.updateById(any(PdmChgRequest.class))).thenReturn(1);

        PdmChgRequestVO vo = chgRequestService.approve(1L, "同意", 10L);

        assertEquals(PdmChgRequestStatus.APPROVED, vo.getStatus());
        assertEquals(10L, vo.getApproveBy());
        assertEquals("同意", vo.getApproveComment());
        assertNotNull(vo.getApproveTime());
    }

    /**
     * 驳回必须填写审批意见。
     */
    @Test
    void rejectShouldRequireComment() {
        BizException ex = assertThrows(BizException.class, () -> chgRequestService.reject(1L, "  ", 10L));

        assertTrue(ex.getMessage().contains("驳回必须填写审批意见"));
        verify(chgRequestMapper, never()).updateById(any(PdmChgRequest.class));
    }

    /**
     * 仅已通过或已驳回的申请可关闭。
     */
    @Test
    void closeShouldOnlyAllowApprovedOrRejected() {
        PdmChgRequest entity = draft(1L);
        when(chgRequestMapper.selectById(1L)).thenReturn(entity);
        assertThrows(BizException.class, () -> chgRequestService.close(1L));

        entity.setStatus(PdmChgRequestStatus.REJECTED);
        when(chgRequestMapper.updateById(any(PdmChgRequest.class))).thenReturn(1);

        PdmChgRequestVO vo = chgRequestService.close(1L);

        assertEquals(PdmChgRequestStatus.CLOSED, vo.getStatus());
        assertNotNull(vo.getCloseTime());
    }

    /**
     * 影响对象类型非法或缺少标识时拒绝新建。
     */
    @Test
    void createShouldRejectInvalidImpact() {
        assertThrows(BizException.class,
                () -> chgRequestService.create(saveRequest(UNKNOWN_TYPE, 1001L, "P"), 9L));
        assertThrows(BizException.class,
                () -> chgRequestService.create(saveRequest(PART_TYPE, null, null), 9L));
        verify(chgRequestMapper, never()).insert(any(PdmChgRequest.class));
    }

    /**
     * 分页每页上限 200，且影响对象正常回显。
     */
    @Test
    void pageShouldClampSizeAndMapImpact() {
        PdmChgRequest entity = draft(1L);
        entity.setImpactJson("[{\"objectType\":\"PART\",\"objectId\":1001,\"objectCode\":\"P-1\"}]");
        Page<PdmChgRequest> returned = new Page<>(1, 200);
        returned.setRecords(List.of(entity));
        returned.setTotal(1);
        when(chgRequestMapper.selectPage(any(), any())).thenReturn(returned);

        PdmChgRequestQueryRequest query = new PdmChgRequestQueryRequest();
        query.setSize(500);

        assertEquals(1, chgRequestService.page(query).getRecords().size());
        verify(chgRequestMapper).selectPage(argThat(page -> page.getSize() == 200), any());
    }

    /**
     * 构造草稿实体。
     *
     * @param id 申请ID
     * @return 草稿实体
     */
    private static PdmChgRequest draft(Long id) {
        PdmChgRequest entity = new PdmChgRequest();
        entity.setId(id);
        entity.setChangeNo("CR-20261105120000-0001");
        entity.setChangeReason("设计变更");
        entity.setStatus(PdmChgRequestStatus.DRAFT);
        entity.setInitiatorId(9L);
        return entity;
    }

    /**
     * 构造保存请求。
     *
     * @param objectType 对象类型
     * @param objectId   对象ID
     * @param objectCode 对象编码
     * @return 保存请求
     */
    private static PdmChgRequestSaveRequest saveRequest(String objectType, Long objectId, String objectCode) {
        PdmChgRequestSaveRequest request = new PdmChgRequestSaveRequest();
        request.setChangeReason("设计变更");
        request.setChangeScope("零件规格调整");
        PdmChgImpactObjectVO impact = new PdmChgImpactObjectVO();
        impact.setObjectType(objectType);
        impact.setObjectId(objectId);
        impact.setObjectCode(objectCode);
        request.setImpactObjects(List.of(impact));
        return request;
    }
}
