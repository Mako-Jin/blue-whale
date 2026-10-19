package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.vo.FormTableRegistryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormPublishServiceImpl} 表单发布（解析 schema → 建物理表 → 登记 registry）单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormPublishServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormDataEngine formDataEngine;

    @InjectMocks
    private FormPublishServiceImpl formPublishService;

    /**
     * 构造表单定义桩对象。
     *
     * @param id 表单定义ID
     * @param code 表单编码
     * @return 表单定义实体
     */
    private static FormDefinition definition(Long id, String code) {
        FormDefinition definition = new FormDefinition();
        definition.setId(id);
        definition.setCode(code);
        definition.setVersion(1);
        definition.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}");
        return definition;
    }

    @Test
    @DisplayName("publish：解析 schema 建物理表并登记 registry")
    void shouldPublish() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(0L);

        FormTableRegistryVO vo = formPublishService.publish(10L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("CREATE TABLE IF NOT EXISTS `form_data_fm_a_v1`"));
        assertTrue(ddlCaptor.getValue().contains("`name` VARCHAR(255)"));
        verify(formTableRegistryMapper).insert(any(FormTableRegistry.class));
        assertEquals("form_data_fm_a_v1", vo.getPhysicalTable());
        assertEquals(1, vo.getFieldCount().intValue());
    }

    @Test
    @DisplayName("publish：同编码同版本已发布抛参数错误且不建表")
    void shouldThrowWhenAlreadyPublished() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> formPublishService.publish(10L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataEngine, never()).executeDdl(anyString());
    }

    @Test
    @DisplayName("publish：定义不存在抛 NOT_FOUND")
    void shouldThrowWhenDefinitionMissing() {
        when(formDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> formPublishService.publish(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("publish：编码不合规抛参数错误")
    void shouldThrowWhenCodeInvalid() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "Fm-A"));

        BizException ex = assertThrows(BizException.class, () -> formPublishService.publish(10L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataEngine, never()).executeDdl(anyString());
    }

    @Test
    @DisplayName("listTables：返回该定义已发布登记列表")
    void shouldListTables() {
        FormTableRegistry registry = new FormTableRegistry();
        registry.setDefinitionId(10L);
        registry.setPhysicalTable("form_data_fm_a_v1");
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectList(ArgumentMatchers.<Wrapper<FormTableRegistry>>any()))
                .thenReturn(List.of(registry));

        List<FormTableRegistryVO> tables = formPublishService.listTables(10L);

        assertEquals(1, tables.size());
        assertEquals("form_data_fm_a_v1", tables.get(0).getPhysicalTable());
    }
}
