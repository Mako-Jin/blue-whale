package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.pdm.change.dto.PdmChgApprovalCallbackRequest;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgRequestMapper;
import com.bluewhale.pdm.change.support.PdmChgApprovalStatus;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgRequestStatus;
import com.bluewhale.pdm.change.support.PdmChgTraceRecorder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更审批回调单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmChgApprovalCallbackServiceImplTest {

    /** 审批实例ID */
    private static final Long INSTANCE_ID = 500L;

    @Mock
    private PdmChgOrderMapper chgOrderMapper;

    @Mock
    private PdmChgRequestMapper chgRequestMapper;

    @Mock
    private PdmChgTraceRecorder traceRecorder;

    @InjectMocks
    private PdmChgApprovalCallbackServiceImpl callbackService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmChgOrder.class);
        TableInfoHelper.initTableInfo(assistant, PdmChgRequest.class);
    }

    /**
     * 首次通过回调把待审批流转为已通过。
     */
    @Test
    void handleShouldApproveOrder() {
        PdmChgOrder order = approvingOrder();
        when(chgOrderMapper.selectList(any())).thenReturn(List.of(order));

        callbackService.handle(callback(1));

        ArgumentCaptor<PdmChgOrder> captor = ArgumentCaptor.forClass(PdmChgOrder.class);
        verify(chgOrderMapper).updateById(captor.capture());
        assertEquals(PdmChgOrderStatus.APPROVED, captor.getValue().getStatus());
        assertEquals(PdmChgApprovalStatus.APPROVED, captor.getValue().getApprovalStatus());
    }

    /**
     * 重复回调相同状态不再更新。
     */
    @Test
    void handleShouldBeIdempotent() {
        PdmChgOrder order = approvingOrder();
        order.setStatus(PdmChgOrderStatus.APPROVED);
        order.setApprovalStatus(PdmChgApprovalStatus.APPROVED);
        when(chgOrderMapper.selectList(any())).thenReturn(List.of(order));

        callbackService.handle(callback(1));

        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
    }

    /**
     * 未知实例静默忽略。
     */
    @Test
    void handleShouldIgnoreUnknownInstance() {
        when(chgOrderMapper.selectList(any())).thenReturn(List.of());
        when(chgRequestMapper.selectList(any())).thenReturn(List.of());

        callbackService.handle(callback(1));

        verify(chgOrderMapper, never()).updateById(any(PdmChgOrder.class));
        verify(chgRequestMapper, never()).updateById(any(PdmChgRequest.class));
    }

    /**
     * 驳回回调把待审批流转为已驳回。
     */
    @Test
    void handleShouldRejectRequest() {
        PdmChgRequest ecr = new PdmChgRequest();
        ecr.setId(7L);
        ecr.setChangeNo("CR-7");
        ecr.setStatus(PdmChgRequestStatus.SUBMITTED);
        ecr.setApprovalStatus(PdmChgApprovalStatus.RUNNING);
        ecr.setApprovalInstanceId(INSTANCE_ID);
        when(chgOrderMapper.selectList(any())).thenReturn(List.of());
        when(chgRequestMapper.selectList(any())).thenReturn(List.of(ecr));

        callbackService.handle(callback(2));

        ArgumentCaptor<PdmChgRequest> captor = ArgumentCaptor.forClass(PdmChgRequest.class);
        verify(chgRequestMapper).updateById(captor.capture());
        assertEquals(PdmChgRequestStatus.REJECTED, captor.getValue().getStatus());
        assertEquals(PdmChgApprovalStatus.REJECTED, captor.getValue().getApprovalStatus());
    }

    /**
     * 构造回调请求。
     */
    private static PdmChgApprovalCallbackRequest callback(int status) {
        PdmChgApprovalCallbackRequest request = new PdmChgApprovalCallbackRequest();
        request.setInstanceId(INSTANCE_ID);
        request.setStatus(status);
        request.setComment("同意");
        request.setOperatorId(9L);
        return request;
    }

    /**
     * 构造待审批变更单。
     */
    private static PdmChgOrder approvingOrder() {
        PdmChgOrder order = new PdmChgOrder();
        order.setId(8L);
        order.setEcoNo("EO-8");
        order.setStatus(PdmChgOrderStatus.APPROVING);
        order.setApprovalStatus(PdmChgApprovalStatus.RUNNING);
        order.setApprovalInstanceId(INSTANCE_ID);
        order.setImpactJson("[]");
        return order;
    }
}
