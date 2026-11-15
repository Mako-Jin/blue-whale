package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDdlAuditor;
import com.bluewhale.form.support.FormRegistryStatusMarker;
import com.bluewhale.form.model.vo.FormTableRegistryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
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
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormDataEngine formDataEngine;

    @Mock
    private FormDdlAuditor formDdlAuditor;

    @Mock
    private FormRegistryStatusMarker formRegistryStatusMarker;

    @InjectMocks
    private FormPublishServiceImpl formPublishService;

    /**
     * 构造表单定义桩对象（旧模型：无 {@code tableDefinitionId}）。
     *
     * @param id 表单定义ID
     * @param code 表单编码
     * @return 表单定义实体
     */
    private static FormDefinitionPo definition(Long id, String code) {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(id);
        definition.setCode(code);
        definition.setVersion(1);
        definition.setSchemaJson("{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}");
        return definition;
    }

    /**
     * 构造表定义桩对象（元数据模型）。
     *
     * @param id 表定义ID
     * @param tableCode 表编码
     * @return 表定义实体
     */
    private static FormTableDefinitionPo tableDefinition(Long id, String tableCode) {
        FormTableDefinitionPo definition = new FormTableDefinitionPo();
        definition.setId(id);
        definition.setTableCode(tableCode);
        definition.setVersion(1);
        return definition;
    }

    /**
     * 构造列元数据桩对象。
     *
     * @param code 列编码
     * @param name 列名称
     * @param dataType 存储类型
     * @param length 长度
     * @return 列元数据
     */
    private static FormColumnMetaPo column(String code, String name, String dataType, Integer length) {
        FormColumnMetaPo column = new FormColumnMetaPo();
        column.setColumnCode(code);
        column.setColumnName(name);
        column.setDataType(dataType);
        column.setLength(length);
        return column;
    }

    @Test
    @DisplayName("publish：解析 schema 建物理表并登记 registry")
    void shouldPublish() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(0L);

        FormTableRegistryVO vo = formPublishService.publish(10L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("CREATE TABLE IF NOT EXISTS `form_data_fm_a_v1`"));
        assertTrue(ddlCaptor.getValue().contains("`name` VARCHAR(255)"));
        verify(formTableRegistryMapper).insert(any(FormTableRegistryPo.class));
        assertEquals("form_data_fm_a_v1", vo.getPhysicalTable());
        assertEquals(1, vo.getFieldCount().intValue());
    }

    @Test
    @DisplayName("publish：同编码同版本已发布抛参数错误且不建表")
    void shouldThrowWhenAlreadyPublished() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
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
    @DisplayName("publish：DDL 失败时写入失败审计并原样抛出，不登记 registry")
    void shouldRecordDdlFailure() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any())).thenReturn(0L);
        doThrow(new IllegalStateException("ddl boom")).when(formDataEngine).executeDdl(anyString());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> formPublishService.publish(10L));

        assertEquals("ddl boom", ex.getMessage());
        verify(formDdlAuditor).recordFailure(eq("FORM"), eq(10L), anyString(), anyString(), eq("ddl boom"));
        verify(formTableRegistryMapper, never()).insert(any(FormTableRegistryPo.class));
    }

    @Test
    @DisplayName("listTables：返回该定义已发布登记列表")
    void shouldListTables() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setDefinitionId(10L);
        registry.setPhysicalTable("form_data_fm_a_v1");
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition(10L, "fm_a"));
        when(formTableRegistryMapper.selectList(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(List.of(registry));

        List<FormTableRegistryVO> tables = formPublishService.listTables(10L);

        assertEquals(1, tables.size());
        assertEquals("form_data_fm_a_v1", tables.get(0).getPhysicalTable());
    }

    @Test
    @DisplayName("publishTableDefinition：物理表不存在则建表并登记 registry")
    void shouldCreateTableWhenPhysicalMissing() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "number", null),
                        column("remark", "备注", "text", 255)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1")).thenReturn(Map.of());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        FormTableRegistryVO vo = formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("CREATE TABLE IF NOT EXISTS `form_data_biz_order_v1`"));
        ArgumentCaptor<FormTableRegistryPo> registryCaptor = ArgumentCaptor.forClass(FormTableRegistryPo.class);
        verify(formTableRegistryMapper).insert(registryCaptor.capture());
        assertTrue(registryCaptor.getValue().getSchemaJson().contains("\"amount\""));
        verify(formTableRegistryMapper, never()).updateById(any(FormTableRegistryPo.class));
        assertEquals(2, vo.getFieldCount().intValue());
    }

    @Test
    @DisplayName("publishTableDefinition：物理表缺列则 ADD COLUMN")
    void shouldAlterAddMissingColumn() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "text", 255),
                        column("remark", "备注", "text", 255)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "varchar(255)"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("ADD COLUMN `remark`"));
        verify(formTableRegistryMapper).insert(any(FormTableRegistryPo.class));
    }

    @Test
    @DisplayName("publishTableDefinition：列类型不一致则 MODIFY COLUMN")
    void shouldAlterModifyChangedColumn() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "text", 255)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "varchar(64)"));
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setId(1L);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry);

        formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("MODIFY COLUMN `amount`"));
        verify(formTableRegistryMapper).updateById(registry);
    }

    @Test
    @DisplayName("publishTableDefinition：元数据外的非公共列则 DROP COLUMN")
    void shouldAlterDropOrphanColumn() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "text", 255)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "varchar(255)", "legacy", "varchar(255)"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("DROP COLUMN `legacy`"));
    }

    @Test
    @DisplayName("publishTableDefinition：物理表公共列不参与 DROP")
    void shouldNotDropReservedColumn() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "text", 255)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "varchar(64)", "create_time", "datetime"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getValue().contains("MODIFY COLUMN `amount`"));
        assertFalse(ddlCaptor.getValue().contains("DROP COLUMN"));
    }

    @Test
    @DisplayName("publishTableDefinition：停用（status=0）列仍在元数据中，保物理列不下发 DROP")
    void shouldNotDropDisabledColumn() {
        FormColumnMetaPo disabled = column("legacy", "历史列", "text", 255);
        disabled.setStatus(0);
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        // 第 1 次 selectList（listPublishColumns，仅 status=1）→ 目标列；第 2 次（listMetadataCodes，不限状态）→ 含停用列
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "text", 255)), List.of(
                        column("amount", "金额", "text", 255), disabled));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "varchar(255)", "legacy", "varchar(255)"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        verify(formDataEngine, never()).executeDdl(anyString());
    }

    @Test
    @DisplayName("publishTableDefinition：无列元数据抛参数错误且不执行 DDL")
    void shouldThrowWhenNoColumns() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> formPublishService.publishTableDefinition(11L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formDataEngine, never()).executeDdl(anyString());
    }

    @Test
    @DisplayName("publishTableDefinition：登记已存在则刷新列数走 updateById")
    void shouldUpdateExistingRegistry() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "text", 255)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "varchar(255)"));
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setId(1L);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry);

        FormTableRegistryVO vo = formPublishService.publishTableDefinition(11L);

        verify(formTableRegistryMapper).updateById(registry);
        verify(formTableRegistryMapper, never()).insert(any(FormTableRegistryPo.class));
        verify(formDataEngine, never()).executeDdl(anyString());
        assertEquals(1, vo.getFieldCount().intValue());
    }

    @Test
    @DisplayName("publishTableDefinition：存量表缺字段级索引则 ADD INDEX")
    void shouldAddMissingAutoIndex() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "number", null)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "decimal(18,4)"));
        when(formDataEngine.describeIndexes("form_data_biz_order_v1"))
                .thenReturn(Set.of("PRIMARY", "idx_tenant_id", "idx_biz_key"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertEquals("ALTER TABLE `form_data_biz_order_v1` ADD INDEX `idx_f_amount` (`amount`)",
                ddlCaptor.getValue());
    }

    @Test
    @DisplayName("publishTableDefinition：列不再满足索引条件则回收自动索引")
    void shouldDropStaleAutoIndex() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("remark", "备注", "textarea", null)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("remark", "text"));
        when(formDataEngine.describeIndexes("form_data_biz_order_v1"))
                .thenReturn(Set.of("PRIMARY", "idx_f_remark", "idx_tenant_id"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine).executeDdl(ddlCaptor.capture());
        assertEquals("ALTER TABLE `form_data_biz_order_v1` DROP INDEX `idx_f_remark`",
                ddlCaptor.getValue());
    }

    @Test
    @DisplayName("publishTableDefinition：索引已对齐时不重复下发 DDL")
    void shouldSkipAlignedAutoIndex() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("amount", "金额", "number", null)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("amount", "decimal(18,4)"));
        when(formDataEngine.describeIndexes("form_data_biz_order_v1"))
                .thenReturn(Set.of("PRIMARY", "idx_f_amount"));
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(null);

        formPublishService.publishTableDefinition(11L);

        verify(formDataEngine, never()).executeDdl(anyString());
    }

    @Test
    @DisplayName("publishTableDefinition：列类型收窄未确认时拒绝发布且不执行任何 DDL")
    void shouldRejectNarrowingWithoutForce() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("remark", "备注", "text", 100)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("remark", "varchar(255)"));

        BizException ex = assertThrows(BizException.class,
                () -> formPublishService.publishTableDefinition(11L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("force=true"));
        assertTrue(ex.getMessage().contains("remark"));
        verify(formDataEngine, never()).executeDdl(anyString());
    }

    @Test
    @DisplayName("publishTableDefinition：显式 force=true 时允许执行收窄的 MODIFY COLUMN")
    void shouldAllowNarrowingWithForce() {
        when(formTableDefinitionMapper.selectById(11L)).thenReturn(tableDefinition(11L, "biz_order"));
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMetaPo>>any()))
                .thenReturn(List.of(column("remark", "备注", "text", 100)));
        when(formDataEngine.describeColumns("form_data_biz_order_v1"))
                .thenReturn(Map.of("remark", "varchar(255)"));
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setId(1L);
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry);

        formPublishService.publishTableDefinition(11L, true);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(formDataEngine, atLeastOnce()).executeDdl(ddlCaptor.capture());
        assertTrue(ddlCaptor.getAllValues().stream()
                .anyMatch(ddl -> ddl.contains("MODIFY COLUMN `remark`") && ddl.contains("VARCHAR(100)")));
    }
}
