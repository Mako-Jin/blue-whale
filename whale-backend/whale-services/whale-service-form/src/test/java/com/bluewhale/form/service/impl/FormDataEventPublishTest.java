package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.form.event.FormDataChangedEvent;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormDataApprovalStartRequest;
import com.bluewhale.form.model.request.FormDataSubmitRequest;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDataServiceImpl} 领域事件发布单元测试（提交 / 修改 / 删除 / 审批）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataEventPublishTest {

    private static final long TENANT = 1L;
    private static final String CODE = "fm_a";
    private static final String TABLE = "form_data_fm_a_v1";

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
    private FormDataHistoryRecorder formDataHistoryRecorder;

    @Mock
    private FormDataEngine formDataEngine;

    @Mock
    private FormMetrics formMetrics;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private FormMetadataCache metadataCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FormDataServiceImpl formDataService;

    /**
     * 构造表单定义桩对象
     *
     * @return 表单定义实体
     */
    private static FormDefinitionPo definition() {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(10L);
        definition.setCode(CODE);
        definition.setVersion(1);
        definition.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}");
        return definition;
    }

    /**
     * 构造物理表登记桩对象
     *
     * @return 登记实体
     */
    private static FormTableRegistryPo registry() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setDefinitionId(10L);
        registry.setDefinitionCode(CODE);
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}");
        return registry;
    }

    /**
     * 构造已落库表单数据桩对象
     *
     * @param id 主键ID
     * @param dataJson 表单数据JSON
     * @return 表单数据实体
     */
    private static FormDataPo existingData(Long id, String dataJson) {
        FormDataPo entity = new FormDataPo();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setDefinitionId(10L);
        entity.setDefinitionCode(CODE);
        entity.setDefinitionVersion(1);
        entity.setDataJson(dataJson);
        entity.setBizStatus(3);
        return entity;
    }

    /**
     * 捕获发布的事件
     *
     * @return 捕获到的事件
     */
    private FormDataChangedEvent captureEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (FormDataChangedEvent) captor.getValue();
    }

    @Test
    @DisplayName("submit：发布 CREATE 事件并携带操作人")
    void shouldPublishCreateEventOnSubmit() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataSubmitRequest request = new FormDataSubmitRequest();
        request.setDefinitionId(10L);
        request.setDataJson("{\"name\":\"张三\"}");

        formDataService.submit(request, "lisi", TENANT);

        FormDataChangedEvent event = captureEvent();
        assertEquals(FormDataChangedEvent.CREATE, event.changeType());
        assertEquals("lisi", event.operator());
        assertEquals(TENANT, event.tenantId());
        assertEquals(CODE, event.definitionCode());
    }

    @Test
    @DisplayName("submit：幂等命中时不发布事件")
    void shouldNotPublishEventWhenIdempotentHit() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any()))
                .thenReturn(existingData(7L, "{\"name\":\"张三\"}"));
        FormDataSubmitRequest request = new FormDataSubmitRequest();
        request.setDefinitionId(10L);
        request.setDataJson("{\"name\":\"张三\"}");
        request.setClientRequestId("REQ-1");

        formDataService.submit(request, "lisi", TENANT);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("update：发布 UPDATE 事件")
    void shouldPublishUpdateEvent() {
        when(formDataMapper.selectById(1L)).thenReturn(existingData(1L, "{\"name\":\"旧\"}"));
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataUpdateRequest request = new FormDataUpdateRequest();
        request.setDataJson("{\"name\":\"新\"}");

        formDataService.update(1L, request, TENANT);

        assertEquals(FormDataChangedEvent.UPDATE, captureEvent().changeType());
    }

    @Test
    @DisplayName("delete：发布 DELETE 事件")
    void shouldPublishDeleteEvent() {
        when(formDataMapper.selectById(7L)).thenReturn(existingData(7L, "{\"name\":\"张三\"}"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());

        formDataService.delete(7L, TENANT);

        assertEquals(FormDataChangedEvent.DELETE, captureEvent().changeType());
    }

    @Test
    @DisplayName("startApproval：发布 APPROVAL 事件")
    void shouldPublishApprovalEvent() {
        when(formDataMapper.selectById(7L)).thenReturn(existingData(7L, "{\"name\":\"张三\"}"));
        FormDataApprovalStartRequest request = new FormDataApprovalStartRequest();
        request.setProcessInstanceId("PI-1");

        formDataService.startApproval(7L, request, TENANT);

        assertEquals(FormDataChangedEvent.APPROVAL, captureEvent().changeType());
    }
}
