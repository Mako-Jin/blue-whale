package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.vo.FormTableRegistryVO;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormArchiveServiceImpl} 物理表归档标记单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormArchiveServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @InjectMocks
    private FormArchiveServiceImpl formArchiveService;

    /**
     * 构造登记桩对象
     *
     * @param id 登记ID
     * @param version 表版本
     * @return 登记实体
     */
    private static FormTableRegistryPo registry(Long id, Integer version) {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setId(id);
        registry.setTableDefinitionId(5L);
        registry.setTableCode("t_order");
        registry.setTableVersion(version);
        registry.setPhysicalTable("form_data_t_order_v" + version);
        registry.setStatus(1);
        registry.setArchiveStatus(0);
        return registry;
    }

    @Test
    @DisplayName("archive：历史版本可归档并写入归档时间")
    void shouldArchiveHistoryVersion() {
        FormTableRegistryPo history = registry(100L, 1);
        when(formTableRegistryMapper.selectById(100L)).thenReturn(history);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry(101L, 2));

        FormTableRegistryVO vo = formArchiveService.archive(5L, 100L);

        assertEquals(1, vo.getArchiveStatus());
        assertNotNull(vo.getArchiveTime());
        ArgumentCaptor<FormTableRegistryPo> captor = ArgumentCaptor.forClass(FormTableRegistryPo.class);
        verify(formTableRegistryMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getArchiveStatus());
    }

    @Test
    @DisplayName("archive：当前生效版本拒绝归档")
    void shouldRejectArchivingActiveVersion() {
        FormTableRegistryPo current = registry(101L, 2);
        when(formTableRegistryMapper.selectById(101L)).thenReturn(current);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(current);

        BizException e = assertThrows(BizException.class, () -> formArchiveService.archive(5L, 101L));

        assertEquals(PARAM_ERROR_CODE, e.getCode());
        verify(formTableRegistryMapper, never()).updateById(any(FormTableRegistryPo.class));
    }

    @Test
    @DisplayName("archive：登记不属于该表定义时返回 NOT_FOUND")
    void shouldRejectMismatchedTableDefinition() {
        when(formTableRegistryMapper.selectById(100L)).thenReturn(registry(100L, 1));

        BizException e = assertThrows(BizException.class, () -> formArchiveService.archive(9L, 100L));

        assertEquals(NOT_FOUND_CODE, e.getCode());
    }

    @Test
    @DisplayName("archive：已归档登记幂等返回，不重复写库")
    void shouldReturnArchivedIdempotently() {
        FormTableRegistryPo archived = registry(100L, 1);
        archived.setArchiveStatus(1);
        when(formTableRegistryMapper.selectById(100L)).thenReturn(archived);

        FormTableRegistryVO vo = formArchiveService.archive(5L, 100L);

        assertEquals(1, vo.getArchiveStatus());
        verify(formTableRegistryMapper, never()).updateById(any(FormTableRegistryPo.class));
    }

    @Test
    @DisplayName("unarchive：取消归档标记并清空时间")
    void shouldUnarchive() {
        FormTableRegistryPo archived = registry(100L, 1);
        archived.setArchiveStatus(1);
        when(formTableRegistryMapper.selectById(100L)).thenReturn(archived);

        FormTableRegistryVO vo = formArchiveService.unarchive(5L, 100L);

        assertEquals(0, vo.getArchiveStatus());
        assertNull(vo.getArchiveTime());
        verify(formTableRegistryMapper).updateById(archived);
    }

    @Test
    @DisplayName("旧模型：当前表单版本不允许归档")
    void shouldRejectArchivingLegacyCurrentVersion() {
        FormTableRegistryPo legacy = new FormTableRegistryPo();
        legacy.setId(200L);
        legacy.setDefinitionId(10L);
        legacy.setDefinitionCode("fm_a");
        legacy.setDefinitionVersion(1);
        legacy.setStatus(1);
        legacy.setArchiveStatus(0);
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(10L);
        definition.setCode("fm_a");
        definition.setVersion(1);
        when(formTableRegistryMapper.selectById(200L)).thenReturn(legacy);
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition);

        BizException e = assertThrows(BizException.class, () -> formArchiveService.archive(null, 200L));

        assertEquals(PARAM_ERROR_CODE, e.getCode());
    }
}
