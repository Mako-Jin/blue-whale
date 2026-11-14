package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.form.entity.FormColumnMetaEntity;
import com.bluewhale.form.entity.FormDataEntity;
import com.bluewhale.form.entity.FormDefinitionEntity;
import com.bluewhale.form.entity.FormFieldConfigEntity;
import com.bluewhale.form.entity.FormFieldOptionEntity;
import com.bluewhale.form.entity.FormTableDefinitionEntity;
import com.bluewhale.form.entity.FormTableRegistryEntity;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormFieldOptionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormMetadataMigrationServiceImpl} 历史 {@code schema_json} → 三层元数据迁移单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormMetadataMigrationServiceImplTest {

    /** 含 text 与 select（带选项）的 legacy schema */
    private static final String SCHEMA =
            "{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\",\"required\":true,\"maxLength\":50},"
                    + "{\"key\":\"grade\",\"label\":\"等级\",\"type\":\"select\",\"options\":[\"A\",\"B\"]}]}";

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormFieldConfigMapper formFieldConfigMapper;

    @Mock
    private FormFieldOptionMapper formFieldOptionMapper;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormDataMapper formDataMapper;

    @InjectMocks
    private FormMetadataMigrationServiceImpl migrationService;

    /**
     * 注册实体元信息缓存：`LambdaUpdateWrapper#set` 会立即按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FormDefinitionEntity.class);
        TableInfoHelper.initTableInfo(assistant, FormTableRegistryEntity.class);
        TableInfoHelper.initTableInfo(assistant, FormDataEntity.class);
    }

    /**
     * 构造 legacy 表单定义桩对象。
     *
     * @param code 表单编码
     * @param schemaJson 结构定义
     * @return 表单定义实体
     */
    private static FormDefinitionEntity legacyForm(String code, String schemaJson) {
        FormDefinitionEntity form = new FormDefinitionEntity();
        form.setId(10L);
        form.setCode(code);
        form.setName("A表单");
        form.setVersion(1);
        form.setSchemaJson(schemaJson);
        return form;
    }

    @Test
    @DisplayName("migrateLegacy：反推表定义 + 列 + 字段配置 + 选项并回填")
    void shouldMigrateLegacyForm() {
        when(formDefinitionMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(legacyForm("form_a", SCHEMA)));
        when(formTableDefinitionMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());

        int migrated = migrationService.migrateLegacy();

        assertEquals(1, migrated);
        verify(formTableDefinitionMapper).insert(any(FormTableDefinitionEntity.class));
        verify(formColumnMetaMapper, times(2)).insert(any(FormColumnMetaEntity.class));
        verify(formFieldConfigMapper, times(2)).insert(any(FormFieldConfigEntity.class));
        verify(formFieldOptionMapper, times(2)).insert(any(FormFieldOptionEntity.class));
        verify(formDefinitionMapper).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(formTableRegistryMapper).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
        verify(formDataMapper).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("migrateLegacy：编码不可作物理表名时跳过且不落库")
    void shouldSkipNonPublishableCode() {
        when(formDefinitionMapper.selectList(ArgumentMatchers.any()))
                .thenReturn(List.of(legacyForm("Bad Code", SCHEMA)));

        int migrated = migrationService.migrateLegacy();

        assertEquals(0, migrated);
        verify(formTableDefinitionMapper, never()).insert(any(FormTableDefinitionEntity.class));
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMetaEntity.class));
    }
}
