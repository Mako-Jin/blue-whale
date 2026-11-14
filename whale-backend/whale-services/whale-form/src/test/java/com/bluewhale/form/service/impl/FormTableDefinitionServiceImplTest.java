package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.dto.FormColumnMetaDTO;
import com.bluewhale.form.dto.FormTableDefinitionSaveRequest;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormFieldConfig;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.vo.FormTableDefinitionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormTableDefinitionServiceImpl} 表定义（含列）聚合 CRUD 单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormTableDefinitionServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Mock
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormFieldConfigMapper formFieldConfigMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @InjectMocks
    private FormTableDefinitionServiceImpl formTableDefinitionService;

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）。
     *
     * <p>纯 Mockito 单测不启动 Spring/MyBatis 上下文，{@code LambdaQueryWrapper} 解析列名依赖
     * 全局 {@code TableInfo} 缓存。缓存由其他测试类填充时顺序相关，在 Linux（CI）与 Windows
     * 下执行顺序不同，会出现 {@code can not find lambda cache for this entity} 的偶发失败；
     * 故在此显式初始化本类用到的全部实体。</p>
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FormTableDefinition.class);
        TableInfoHelper.initTableInfo(assistant, FormColumnMeta.class);
        TableInfoHelper.initTableInfo(assistant, FormFieldConfig.class);
        TableInfoHelper.initTableInfo(assistant, FormDefinition.class);
    }

    /**
     * 构造列请求桩对象。
     *
     * @param code 列编码
     * @param dataType 存储类型
     * @return 列请求
     */
    private static FormColumnMetaDTO column(String code, String dataType) {
        FormColumnMetaDTO dto = new FormColumnMetaDTO();
        dto.setColumnCode(code);
        dto.setColumnName(code);
        dto.setDataType(dataType);
        return dto;
    }

    /**
     * 构造表定义保存请求桩对象。
     *
     * @param columns 列请求列表
     * @return 表定义保存请求
     */
    private static FormTableDefinitionSaveRequest saveRequest(List<FormColumnMetaDTO> columns) {
        FormTableDefinitionSaveRequest request = new FormTableDefinitionSaveRequest();
        request.setTableCode("biz_order");
        request.setTableName("业务订单");
        request.setColumns(columns);
        return request;
    }

    /**
     * 构造已落库表定义桩对象（编码 {@code biz_order}，与请求编码一致以避免唯一性校验）。
     *
     * @return 表定义实体
     */
    private static FormTableDefinition tableDefinition() {
        FormTableDefinition entity = new FormTableDefinition();
        entity.setId(10L);
        entity.setTableCode("biz_order");
        entity.setTableName("业务订单");
        entity.setVersion(1);
        return entity;
    }

    /**
     * 构造已落库列元数据桩对象。
     *
     * @param id 列主键
     * @param code 列编码
     * @return 列元数据实体
     */
    private static FormColumnMeta existingColumn(Long id, String code) {
        FormColumnMeta column = new FormColumnMeta();
        column.setId(id);
        column.setTableDefinitionId(10L);
        column.setColumnCode(code);
        return column;
    }

    @Test
    @DisplayName("create：编码唯一时落库表定义并逐列落库")
    void shouldCreateTableAndColumns() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(0L);

        FormTableDefinitionVO vo = formTableDefinitionService.create(
                saveRequest(List.of(column("amount", "number"), column("remark", "text"))));

        verify(formTableDefinitionMapper).insert(any(FormTableDefinition.class));
        verify(formColumnMetaMapper, times(2)).insert(any(FormColumnMeta.class));
        assertEquals("biz_order", vo.getTableCode());
        assertEquals(2, vo.getColumns().size());
    }

    @Test
    @DisplayName("create：表编码已存在抛参数错误且不落库")
    void shouldRejectDuplicateTableCode() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> formTableDefinitionService.create(saveRequest(List.of(column("amount", "number")))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formTableDefinitionMapper, never()).insert(any(FormTableDefinition.class));
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMeta.class));
    }

    @Test
    @DisplayName("create：非法存储类型抛参数错误")
    void shouldRejectInvalidDataType() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> formTableDefinitionService.create(saveRequest(List.of(column("amount", "bad_type")))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMeta.class));
    }

    @Test
    @DisplayName("create：required=1 且 nullable=1 抛参数错误")
    void shouldRejectRequiredNullableConflict() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(0L);
        FormColumnMetaDTO dto = column("amount", "number");
        dto.setRequired(1);
        dto.setNullable(1);

        BizException ex = assertThrows(BizException.class,
                () -> formTableDefinitionService.create(saveRequest(List.of(dto))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMeta.class));
    }

    @Test
    @DisplayName("create：required=1 未指定 nullable 时推导为非空")
    void shouldDeriveNullableFromRequired() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(0L);
        FormColumnMetaDTO dto = column("amount", "number");
        dto.setRequired(1);

        formTableDefinitionService.create(saveRequest(List.of(dto)));

        ArgumentCaptor<FormColumnMeta> captor = ArgumentCaptor.forClass(FormColumnMeta.class);
        verify(formColumnMetaMapper).insert(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getNullable());
    }

    @Test
    @DisplayName("update：同编码列就地合并并保留原主键")
    void shouldKeepExistingColumnId() {
        when(formTableDefinitionMapper.selectById(10L)).thenReturn(tableDefinition());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any()))
                .thenReturn(List.of(existingColumn(100L, "amount")));

        formTableDefinitionService.update(10L, saveRequest(List.of(column("amount", "text"))));

        ArgumentCaptor<FormColumnMeta> captor = ArgumentCaptor.forClass(FormColumnMeta.class);
        verify(formColumnMetaMapper).updateById(captor.capture());
        assertEquals(100L, captor.getValue().getId());
        verify(formColumnMetaMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("update：新增列落库")
    void shouldInsertNewColumn() {
        when(formTableDefinitionMapper.selectById(10L)).thenReturn(tableDefinition());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any()))
                .thenReturn(List.of(existingColumn(100L, "amount")));

        formTableDefinitionService.update(10L,
                saveRequest(List.of(column("amount", "text"), column("remark", "text"))));

        ArgumentCaptor<FormColumnMeta> captor = ArgumentCaptor.forClass(FormColumnMeta.class);
        verify(formColumnMetaMapper).insert(captor.capture());
        assertEquals("remark", captor.getValue().getColumnCode());
    }

    @Test
    @DisplayName("update：待删列被表单视图引用抛参数错误且不删除")
    void shouldRejectDeletingReferencedColumn() {
        when(formTableDefinitionMapper.selectById(10L)).thenReturn(tableDefinition());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any()))
                .thenReturn(List.of(existingColumn(100L, "amount")));
        FormFieldConfig config = new FormFieldConfig();
        config.setFormId(5L);
        config.setColumnMetaId(100L);
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfig>>any()))
                .thenReturn(List.of(config));
        FormDefinition form = new FormDefinition();
        form.setId(5L);
        form.setName("订单表单");
        form.setTableDefinitionId(10L);
        when(formDefinitionMapper.selectList(ArgumentMatchers.<Wrapper<FormDefinition>>any()))
                .thenReturn(List.of(form));

        BizException ex = assertThrows(BizException.class,
                () -> formTableDefinitionService.update(10L, saveRequest(List.of(column("remark", "text")))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formColumnMetaMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("update：待删列未被引用则逻辑删除")
    void shouldDeleteUnreferencedColumn() {
        when(formTableDefinitionMapper.selectById(10L)).thenReturn(tableDefinition());
        when(formColumnMetaMapper.selectList(ArgumentMatchers.<Wrapper<FormColumnMeta>>any()))
                .thenReturn(List.of(existingColumn(100L, "amount")));
        when(formFieldConfigMapper.selectList(ArgumentMatchers.<Wrapper<FormFieldConfig>>any()))
                .thenReturn(List.of());

        formTableDefinitionService.update(10L, saveRequest(List.of(column("remark", "text"))));

        verify(formColumnMetaMapper).deleteById(100L);
    }

    @Test
    @DisplayName("delete：表定义被表单引用时拒绝删除")
    void shouldRejectDeleteWhenReferenced() {
        when(formTableDefinitionMapper.selectById(10L)).thenReturn(tableDefinition());
        when(formDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormDefinition>>any())).thenReturn(2L);

        BizException ex = assertThrows(BizException.class, () -> formTableDefinitionService.delete(10L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formTableDefinitionMapper, never()).deleteById(anyLong());
        verify(formColumnMetaMapper, never()).delete(ArgumentMatchers.<Wrapper<FormColumnMeta>>any());
    }
}
