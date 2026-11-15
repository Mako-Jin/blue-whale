package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.mapper.FormAttachmentRefMapper;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
import com.bluewhale.form.model.vo.FormDataVO;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataHistoryRecorder;
import com.bluewhale.form.support.FormMetadataCache;
import com.bluewhale.form.support.FormMetrics;
import com.bluewhale.form.support.FormRoleContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字段级角色权限在详情裁剪与修改校验上的行为测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataFieldPermissionTest {

    private static final long TENANT = 1L;
    private static final String TABLE = "form_data_fm_a_v1";

    /** 手机号仅 hr 可见；金额仅 admin 可编辑；薪资仅 admin 可见；姓名不限制 */
    private static final String SCHEMA = "{\"fields\":["
            + "{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"},"
            + "{\"key\":\"phone\",\"label\":\"手机号\",\"type\":\"text\",\"visibleRoles\":[\"hr\"]},"
            + "{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"number\",\"editableRoles\":[\"admin\"]},"
            + "{\"key\":\"salary\",\"label\":\"薪资\",\"type\":\"number\",\"visibleRoles\":[\"admin\"]}]}";

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
    private FormMetadataCache metadataCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FormDataServiceImpl formDataService;

    @AfterEach
    void clear() {
        FormRoleContext.clear();
    }

    /**
     * 构造表单定义桩对象
     *
     * @return 表单定义
     */
    private static FormDefinitionPo definition() {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(10L);
        definition.setCode("fm_a");
        definition.setVersion(1);
        definition.setSchemaJson(SCHEMA);
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
        registry.setDefinitionCode("fm_a");
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson(SCHEMA);
        return registry;
    }

    /**
     * 构造表单数据桩对象
     *
     * @return 表单数据
     */
    private static FormDataPo data() {
        FormDataPo entity = new FormDataPo();
        entity.setId(1L);
        entity.setTenantId(TENANT);
        entity.setDefinitionId(10L);
        entity.setDefinitionCode("fm_a");
        entity.setDefinitionVersion(1);
        entity.setDataJson("{\"name\":\"张三\",\"phone\":\"13812345678\",\"amount\":100,\"salary\":8000}");
        return entity;
    }

    @Test
    @DisplayName("详情：按角色裁剪不可见字段，并返回只读字段")
    void shouldFilterInvisibleFieldsOnDetail() {
        FormRoleContext.set(Set.of("hr"));
        when(formDataMapper.selectById(1L)).thenReturn(data());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        when(formDataEngine.selectFields(any(), eq(1L), eq(TENANT), anyList()))
                .thenReturn(Map.of("name", "张三", "phone", "13812345678", "amount", 100));

        FormDataVO vo = formDataService.getDetail(1L, TENANT);

        assertTrue(vo.getFields().containsKey("phone"), "hr 可见手机号");
        assertFalse(vo.getFields().containsKey("salary"), "薪资仅 admin 可见，不应返回");
        assertEquals(List.of("amount"), vo.getReadonlyFields());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.bluewhale.form.support.FormField>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(formDataEngine).selectFields(any(), eq(1L), eq(TENANT), captor.capture());
        assertEquals(List.of("name", "phone", "amount"),
                captor.getValue().stream().map(com.bluewhale.form.support.FormField::getKey).toList());
    }

    @Test
    @DisplayName("修改：当前角色不可编辑的字段被改动时拒绝")
    void shouldRejectChangingNonEditableField() {
        FormRoleContext.set(Set.of("hr"));
        when(formDataMapper.selectById(1L)).thenReturn(data());
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataUpdateRequest request = new FormDataUpdateRequest();
        request.setDataJson("{\"amount\":999}");

        BizException e = assertThrows(BizException.class, () -> formDataService.update(1L, request, TENANT));

        assertEquals(400, e.getCode());
        verify(formDataMapper, never()).updateById(any(FormDataPo.class));
    }

    @Test
    @DisplayName("修改：不可编辑字段值未变化时放行")
    void shouldAllowUnchangedNonEditableField() {
        FormRoleContext.set(Set.of("hr"));
        when(formDataMapper.selectById(1L)).thenReturn(data());
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataUpdateRequest request = new FormDataUpdateRequest();
        request.setDataJson("{\"name\":\"李四\"}");

        FormDataVO vo = formDataService.update(1L, request, TENANT);

        assertEquals("李四", vo.getFields().get("name"));
        assertEquals(List.of("amount"), vo.getReadonlyFields());
    }
}
