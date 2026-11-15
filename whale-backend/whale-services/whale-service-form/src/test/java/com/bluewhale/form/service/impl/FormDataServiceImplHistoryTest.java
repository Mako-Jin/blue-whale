package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormDataSubmitRequest;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
import com.bluewhale.form.model.vo.FormDataHistoryVO;
import com.bluewhale.form.po.FormDataHistoryPo;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataHistoryRecorder;
import com.bluewhale.form.support.FormMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDataServiceImpl} 表单数据变更历史留痕单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataServiceImplHistoryTest {

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

    @InjectMocks
    private FormDataServiceImpl formDataService;

    /**
     * 构造表单定义桩对象。
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
     * 构造物理表登记桩对象。
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
     * 构造已落库表单数据桩对象。
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
        entity.setSubmitter("lisi");
        return entity;
    }

    @Test
    @DisplayName("submit：成功后写入 CREATE 留痕")
    void shouldRecordCreateOnSubmit() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataSubmitRequest request = new FormDataSubmitRequest();
        request.setDefinitionId(10L);
        request.setDataJson("{\"name\":\"张三\"}");

        formDataService.submit(request, "lisi", TENANT);

        verify(formDataHistoryRecorder).recordCreate(any(FormDataPo.class));
    }

    @Test
    @DisplayName("submit：幂等命中时不再重复写入 CREATE 留痕")
    void shouldNotRecordCreateWhenIdempotentHit() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formDataMapper.selectOne(ArgumentMatchers.<Wrapper<FormDataPo>>any()))
                .thenReturn(existingData(7L, "{\"name\":\"张三\"}"));
        FormDataSubmitRequest request = new FormDataSubmitRequest();
        request.setDefinitionId(10L);
        request.setDataJson("{\"name\":\"张三\"}");
        request.setClientRequestId("REQ-1");

        formDataService.submit(request, "lisi", TENANT);

        verify(formDataHistoryRecorder, never()).recordCreate(any(FormDataPo.class));
        verify(formDataMapper, never()).insert(any(FormDataPo.class));
    }

    @Test
    @DisplayName("update：写入 UPDATE 留痕且携带变更前快照")
    void shouldRecordUpdateWithBeforeSnapshot() {
        String beforeJson = "{\"name\":\"旧\"}";
        when(formDataMapper.selectById(1L)).thenReturn(existingData(1L, beforeJson));
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataUpdateRequest request = new FormDataUpdateRequest();
        request.setDataJson("{\"name\":\"新\"}");

        formDataService.update(1L, request, TENANT);

        ArgumentCaptor<FormDataPo> entityCaptor = ArgumentCaptor.forClass(FormDataPo.class);
        verify(formDataHistoryRecorder).recordUpdate(entityCaptor.capture(), eq(beforeJson));
        assertEquals("{\"name\":\"新\"}", entityCaptor.getValue().getDataJson());
        verify(formDataEngine).update(eq(TABLE), any(FormDataPo.class), anyList(), anyMap());
    }

    @Test
    @DisplayName("delete：删除前写入 DELETE 留痕，且先留痕后删主表")
    void shouldRecordDeleteBeforeRemoving() {
        FormDataPo entity = existingData(7L, "{\"name\":\"张三\"}");
        when(formDataMapper.selectById(7L)).thenReturn(entity);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());

        formDataService.delete(7L, TENANT);

        InOrder order = inOrder(formDataHistoryRecorder, formDataMapper);
        order.verify(formDataHistoryRecorder).recordDelete(entity);
        order.verify(formDataMapper).deleteById(7L);
        verify(formDataEngine).logicDelete(TABLE, 7L, TENANT);
    }

    @Test
    @DisplayName("listHistory：按租户与数据ID查询并映射为视图对象")
    void shouldListHistory() {
        FormDataHistoryPo created = new FormDataHistoryPo();
        created.setId(2L);
        created.setDataId(7L);
        created.setChangeType("CREATE");
        created.setChangeTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        FormDataHistoryPo updated = new FormDataHistoryPo();
        updated.setId(3L);
        updated.setDataId(7L);
        updated.setChangeType("UPDATE");
        updated.setChangedKeys("name");
        updated.setChangeTime(LocalDateTime.of(2026, 1, 2, 10, 0));
        when(formDataHistoryMapper.selectList(ArgumentMatchers.<Wrapper<FormDataHistoryPo>>any()))
                .thenReturn(List.of(updated, created));

        List<FormDataHistoryVO> history = formDataService.listHistory(7L, TENANT);

        assertEquals(2, history.size());
        assertEquals("UPDATE", history.get(0).getChangeType());
        assertEquals("name", history.get(0).getChangedKeys());
        assertEquals("CREATE", history.get(1).getChangeType());
        verify(formDataHistoryMapper).selectList(ArgumentMatchers.<Wrapper<FormDataHistoryPo>>any());
    }
}
