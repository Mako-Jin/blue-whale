package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormFieldConfigPo;
import com.bluewhale.form.po.FormFieldOptionPo;
import com.bluewhale.form.po.FormTableDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormFieldOptionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.support.FormMetadataCache;
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

    @Mock
    private FormMetadataCache metadataCache;

    @InjectMocks
    private FormMetadataMigrationServiceImpl migrationService;

    /**
     * 注册实体元信息缓存：`LambdaUpdateWrapper#set` 会立即按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FormDefinitionPo.class);
        TableInfoHelper.initTableInfo(assistant, FormTableRegistryPo.class);
        TableInfoHelper.initTableInfo(assistant, FormDataPo.class);
    }

    /**
     * 构造 legacy 表单定义桩对象。
     *
     * @param code 表单编码
     * @param schemaJson 结构定义
     * @return 表单定义实体
     */
    private static FormDefinitionPo legacyForm(String code, String schemaJson) {
        FormDefinitionPo form = new FormDefinitionPo();
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
        verify(formTableDefinitionMapper).insert(any(FormTableDefinitionPo.class));
        verify(formColumnMetaMapper, times(2)).insert(any(FormColumnMetaPo.class));
        verify(formFieldConfigMapper, times(2)).insert(any(FormFieldConfigPo.class));
        verify(formFieldOptionMapper, times(2)).insert(any(FormFieldOptionPo.class));
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
        verify(formTableDefinitionMapper, never()).insert(any(FormTableDefinitionPo.class));
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMetaPo.class));
    }
}
