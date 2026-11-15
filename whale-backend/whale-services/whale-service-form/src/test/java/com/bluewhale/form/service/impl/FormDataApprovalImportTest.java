package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormAttachmentRefMapper;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormDataApprovalCallbackRequest;
import com.bluewhale.form.model.request.FormDataApprovalStartRequest;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
import com.bluewhale.form.model.vo.FormDataImportResultVO;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataHistoryRecorder;
import com.bluewhale.form.support.FormMetadataCache;
import com.bluewhale.form.support.FormMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDataServiceImpl} 审批状态与批量导入单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataApprovalImportTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final long TENANT = 1L;
    private static final String CODE = "fm_a";
    private static final String TABLE = "form_data_fm_a_v1";
    private static final String NUMBER_SCHEMA =
            "{\"fields\":[{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"number\"}]}";

    @Mock
    private FormDataMapper formDataMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormDataHistoryMapper formDataHistoryMapper;

    @Mock
    private FormAttachmentRefMapper formAttachmentRefMapper;

    @Mock
    private FormDataHistoryRecorder formDataHistoryRecorder;

    @Mock
    private FormDataEngine formDataEngine;

    @Mock
    private FormMetrics formMetrics;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private FormMetadataCache metadataCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FormDataServiceImpl formDataService;

    /**
     * 构造表单定义
     *
     * @return 表单定义实体
     */
    private static FormDefinitionPo definition() {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(10L);
        definition.setCode(CODE);
        definition.setVersion(1);
        definition.setSchemaJson(NUMBER_SCHEMA);
        return definition;
    }

    /**
     * 构造物理表登记
     *
     * @return 登记实体
     */
    private static FormTableRegistryPo registry() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setDefinitionCode(CODE);
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson(NUMBER_SCHEMA);
        registry.setStatus(1);
        return registry;
    }

    /**
     * 构造表单数据
     *
     * @param bizStatus 业务状态
     * @return 表单数据实体
     */
    private static FormDataPo data(Integer bizStatus) {
        FormDataPo entity = new FormDataPo();
        entity.setId(7L);
        entity.setTenantId(TENANT);
        entity.setDefinitionId(10L);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        entity.setDataJson("{\"amount\":1}");
        entity.setSubmitter("lisi");
        entity.setBizStatus(bizStatus);
        return entity;
    }

    @Test
    @DisplayName("update：审批中的表单数据禁止修改")
    void shouldRejectUpdateWhenPending() {
        when(formDataMapper.selectById(7L)).thenReturn(data(2));
        FormDataUpdateRequest request = new FormDataUpdateRequest();
        request.setDataJson("{\"amount\":2}");

        BizException ex = assertThrows(BizException.class,
                () -> formDataService.update(7L, request, TENANT));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataMapper, never()).updateById(any(FormDataPo.class));
    }

    @Test
    @DisplayName("startApproval：登记流程实例并置为审批中，同时写审批留痕")
    void shouldStartApproval() {
        FormDataPo entity = data(3);
        when(formDataMapper.selectById(7L)).thenReturn(entity);
        FormDataApprovalStartRequest request = new FormDataApprovalStartRequest();
        request.setProcessInstanceId("PI-1");

        formDataService.startApproval(7L, request, TENANT);

        assertEquals(2, entity.getBizStatus().intValue());
        assertEquals("PI-1", entity.getProcessInstanceId());
        verify(formDataMapper).updateById(entity);
        verify(formDataHistoryRecorder).recordApproval(entity, 3, null);
    }

    @Test
    @DisplayName("startApproval：已审批中且实例相同幂等返回，不重复更新")
    void shouldBeIdempotentWhenSameInstance() {
        FormDataPo entity = data(2);
        entity.setProcessInstanceId("PI-1");
        when(formDataMapper.selectById(7L)).thenReturn(entity);
        FormDataApprovalStartRequest request = new FormDataApprovalStartRequest();
        request.setProcessInstanceId("PI-1");

        formDataService.startApproval(7L, request, TENANT);

        verify(formDataMapper, never()).updateById(any(FormDataPo.class));
    }

    @Test
    @DisplayName("回调：通过后置为已通过并留痕")
    void shouldApproveOnCallback() {
        FormDataPo entity = data(2);
        entity.setProcessInstanceId("PI-1");
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any())).thenReturn(entity);
        FormDataApprovalCallbackRequest request = new FormDataApprovalCallbackRequest();
        request.setProcessInstanceId("PI-1");
        request.setResult("approved");

        formDataService.handleApprovalCallback(request, TENANT);

        assertEquals(3, entity.getBizStatus().intValue());
        verify(formDataHistoryRecorder).recordApproval(entity, 2, null);
    }

    @Test
    @DisplayName("回调：重复投递（已是终态）幂等忽略")
    void shouldIgnoreRepeatedCallback() {
        FormDataPo entity = data(3);
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any())).thenReturn(entity);
        FormDataApprovalCallbackRequest request = new FormDataApprovalCallbackRequest();
        request.setProcessInstanceId("PI-1");
        request.setResult("REJECTED");

        formDataService.handleApprovalCallback(request, TENANT);

        assertEquals(3, entity.getBizStatus().intValue());
        verify(formDataMapper, never()).updateById(any(FormDataPo.class));
    }

    @Test
    @DisplayName("导入：部分成功模式成功行落库、失败行回显行号与原因")
    void shouldImportPartially() {
        when(formDefinitionMapper.selectList(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any()))
                .thenReturn(List.of(definition()));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        byte[] content = "金额\n100\nabc\n".getBytes(StandardCharsets.UTF_8);

        FormDataImportResultVO result = formDataService.importData(CODE, content, true, "lisi", TENANT);

        assertEquals(2, result.getTotalCount().intValue());
        assertEquals(1, result.getSuccessCount().intValue());
        assertEquals(1, result.getFailCount().intValue());
        assertEquals(3, result.getFailRows().get(0).getRowNo().intValue());
        verify(formDataMapper).insert(any(FormDataPo.class));
        verify(formDataEngine).insert(eq(TABLE), any(FormDataPo.class), anyList(), anyMap());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("导入：整批模式任一行失败即整体回滚，成功数为 0")
    void shouldRollbackWholeImportOnFailure() {
        when(formDefinitionMapper.selectList(ArgumentMatchers.<Wrapper<FormDefinitionPo>>any()))
                .thenReturn(List.of(definition()));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        byte[] content = "金额\n100\nabc\n".getBytes(StandardCharsets.UTF_8);

        FormDataImportResultVO result = formDataService.importData(CODE, content, false, "lisi", TENANT);

        assertEquals(0, result.getSuccessCount().intValue());
        assertEquals(1, result.getFailCount().intValue());
        verify(transactionManager).rollback(transactionStatus);
    }
}
