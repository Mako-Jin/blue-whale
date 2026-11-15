package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.vo.FormSchemaDriftVO;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.support.FormDataEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link FormSchemaDriftServiceImpl} 物理表结构漂移检测单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormSchemaDriftServiceImplTest {

    private static final String TABLE = "form_data_t_order_v1";

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormDataEngine formDataEngine;

    @InjectMocks
    private FormSchemaDriftServiceImpl formSchemaDriftService;

    /**
     * 构造登记桩对象
     *
     * @return 登记实体
     */
    private static FormTableRegistryPo registry() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setId(100L);
        registry.setTableDefinitionId(5L);
        registry.setTableCode("t_order");
        registry.setTableVersion(1);
        registry.setPhysicalTable(TABLE);
        return registry;
    }

    /**
     * 构造列元数据桩对象
     *
     * @param code 列编码
     * @param dataType 存储类型
     * @param length 长度
     * @return 列元数据实体
     */
    private static FormColumnMetaPo column(String code, String dataType, Integer length) {
        FormColumnMetaPo column = new FormColumnMetaPo();
        column.setColumnCode(code);
        column.setColumnName(code);
        column.setDataType(dataType);
        column.setLength(length);
        column.setStatus(1);
        return column;
    }

    @Test
    @DisplayName("inspect：结构一致时 status=OK")
    void shouldReportOkWhenStructureMatches() {
        when(formTableRegistryMapper.selectById(100L)).thenReturn(registry());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("name", "text", 50)));
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("name", "varchar(50)");
        actual.put("id", "bigint");
        actual.put("deleted", "tinyint");
        when(formDataEngine.describeColumns(TABLE)).thenReturn(actual);

        FormSchemaDriftVO vo = formSchemaDriftService.inspect(100L);

        assertEquals(FormSchemaDriftVO.OK, vo.getStatus());
        assertTrue(vo.getMissingInPhysical().isEmpty());
        assertTrue(vo.getMissingInMetadata().isEmpty());
        assertTrue(vo.getTypeMismatches().isEmpty());
    }

    @Test
    @DisplayName("inspect：缺列、多余列与类型不一致均登记为 DRIFT")
    void shouldReportDrift() {
        when(formTableRegistryMapper.selectById(100L)).thenReturn(registry());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("name", "text", 50), column("amount", "number", null)));
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("name", "varchar(20)");
        actual.put("legacy_col", "int");
        when(formDataEngine.describeColumns(TABLE)).thenReturn(actual);

        FormSchemaDriftVO vo = formSchemaDriftService.inspect(100L);

        assertEquals(FormSchemaDriftVO.DRIFT, vo.getStatus());
        assertEquals(List.of("amount"), vo.getMissingInPhysical());
        assertEquals(List.of("legacy_col"), vo.getMissingInMetadata());
        assertEquals(1, vo.getTypeMismatches().size());
        assertEquals("name", vo.getTypeMismatches().get(0).getColumn());
        assertEquals("varchar(50)", vo.getTypeMismatches().get(0).getExpected());
        assertEquals("varchar(20)", vo.getTypeMismatches().get(0).getActual());
    }

    @Test
    @DisplayName("inspect：物理表不存在时 status=TABLE_MISSING")
    void shouldReportTableMissing() {
        when(formTableRegistryMapper.selectById(100L)).thenReturn(registry());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("name", "text", 50)));
        when(formDataEngine.describeColumns(TABLE)).thenReturn(Map.of());

        FormSchemaDriftVO vo = formSchemaDriftService.inspect(100L);

        assertEquals(FormSchemaDriftVO.TABLE_MISSING, vo.getStatus());
        assertEquals(List.of("name"), vo.getMissingInPhysical());
    }

    @Test
    @DisplayName("inspect：旧模型按 schema 快照比对")
    void shouldInspectLegacySchema() {
        FormTableRegistryPo legacy = registry();
        legacy.setTableDefinitionId(null);
        legacy.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"maxLength\":50}]}");
        when(formTableRegistryMapper.selectById(100L)).thenReturn(legacy);
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("name", "varchar(50)");
        when(formDataEngine.describeColumns(TABLE)).thenReturn(actual);

        FormSchemaDriftVO vo = formSchemaDriftService.inspect(100L);

        assertEquals(FormSchemaDriftVO.OK, vo.getStatus());
        assertEquals(1, vo.getExpectedColumnCount());
    }
}
