package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
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
import com.bluewhale.form.service.FormMetadataMigrationService;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.support.FormTableDdlGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单元数据迁移服务实现（{@code schema_json} → 三层元数据，幂等）。
 *
 * <p>迁移不改动物理表与历史数据：因 {@code table_code == form.code} 且 {@code table_version == form.version}，
 * 物理表名 {@code form_data_{table_code}_v{version}} 与 Wave 5.27 完全同构。编码不可作为物理表名的表单定义
 * 将被跳过（无法建表，保留原样）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormMetadataMigrationServiceImpl implements FormMetadataMigrationService {

    /** 状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** JSON 顶层字段数组键名 */
    private static final String KEY_FIELDS = FormSchemaParser.KEY_FIELDS;

    /** 字段选项数组键名 */
    private static final String KEY_OPTIONS = "options";

    /** JSON 解析器（仅读取，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormFieldConfigMapper formFieldConfigMapper;

    private final FormFieldOptionMapper formFieldOptionMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormDataMapper formDataMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateLegacy() {
        LambdaQueryWrapper<FormDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(FormDefinitionEntity::getTableDefinitionId);
        wrapper.isNotNull(FormDefinitionEntity::getSchemaJson);
        List<FormDefinitionEntity> forms = formDefinitionMapper.selectList(wrapper);
        int migrated = 0;
        for (FormDefinitionEntity form : forms) {
            if (StringUtils.hasText(form.getSchemaJson()) && migrateOne(form)) {
                migrated++;
            }
        }
        return migrated;
    }

    /**
     * 迁移单个表单定义
     *
     * @param form 表单定义实体
     * @return 实际迁移返回 {@code true}；编码不可作物理表名而跳过时返回 {@code false}
     */
    private boolean migrateOne(FormDefinitionEntity form) {
        try {
            FormTableDdlGenerator.assertPublishableCode(form.getCode());
        } catch (BizException e) {
            return false;
        }
        List<FormField> fields = FormSchemaParser.parse(form.getSchemaJson());
        Map<String, List<String>> options = parseOptions(form.getSchemaJson());
        FormTableDefinitionEntity table = ensureTableDefinition(form);
        migrateFields(form, table, fields, options);
        backfill(form, table);
        return true;
    }

    /**
     * 复用或新建表定义（幂等：同 {@code tableCode} 已存在则复用）
     *
     * @param form 表单定义实体
     * @return 表定义实体
     */
    private FormTableDefinitionEntity ensureTableDefinition(FormDefinitionEntity form) {
        LambdaQueryWrapper<FormTableDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableDefinitionEntity::getTableCode, form.getCode());
        List<FormTableDefinitionEntity> existing = formTableDefinitionMapper.selectList(wrapper);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        FormTableDefinitionEntity table = new FormTableDefinitionEntity();
        table.setTableCode(form.getCode());
        table.setTableName(form.getName());
        table.setVersion(versionOf(form));
        table.setRemark("由表单定义 schema_json 迁移生成");
        table.setStatus(STATUS_NORMAL);
        formTableDefinitionMapper.insert(table);
        return table;
    }

    /**
     * 按字段列表迁移列元数据、字段配置与选项
     *
     * @param form 表单定义实体
     * @param table 表定义实体
     * @param fields 字段列表（来自于 {@code schema_json}）
     * @param options 字段选项映射（key 为字段 key）
     */
    private void migrateFields(FormDefinitionEntity form, FormTableDefinitionEntity table, List<FormField> fields,
                               Map<String, List<String>> options) {
        Integer version = versionOf(form);
        for (int index = 0; index < fields.size(); index++) {
            FormField field = fields.get(index);
            FormColumnMetaEntity column = insertColumn(table, version, field, index);
            FormFieldConfigEntity config = insertFieldConfig(form, field, column.getId(), index);
            insertOptions(config.getId(), options.get(field.getKey()));
        }
    }

    /**
     * 插入一条列元数据
     *
     * @param table 表定义实体
     * @param version 表版本
     * @param field 字段定义
     * @param sortNo 排序号
     * @return 已落库的列元数据实体（含主键）
     */
    private FormColumnMetaEntity insertColumn(FormTableDefinitionEntity table, Integer version, FormField field, int sortNo) {
        FormColumnMetaEntity column = new FormColumnMetaEntity();
        column.setTableDefinitionId(table.getId());
        column.setTableVersion(version);
        column.setColumnCode(field.getKey());
        column.setColumnName(field.getLabel());
        column.setDataType(field.getType());
        column.setLength(field.getMaxLength());
        column.setRequired(field.isRequired() ? STATUS_NORMAL : 0);
        column.setNullable(field.isRequired() ? 0 : STATUS_NORMAL);
        column.setSortNo(sortNo);
        column.setStatus(STATUS_NORMAL);
        formColumnMetaMapper.insert(column);
        return column;
    }

    /**
     * 插入一条字段配置（控件类型按存储类型默认映射）
     *
     * @param form 表单定义实体
     * @param field 字段定义
     * @param columnMetaId 关联列元数据ID
     * @param sortNo 排序号
     * @return 已落库的字段配置实体（含主键）
     */
    private FormFieldConfigEntity insertFieldConfig(FormDefinitionEntity form, FormField field, Long columnMetaId, int sortNo) {
        FormFieldConfigEntity config = new FormFieldConfigEntity();
        config.setFormId(form.getId());
        config.setColumnMetaId(columnMetaId);
        config.setFieldLabel(field.getLabel());
        config.setWidgetType(FormDataType.defaultWidget(field.getType()));
        config.setShowInList(STATUS_NORMAL);
        config.setShowInForm(STATUS_NORMAL);
        config.setEditable(STATUS_NORMAL);
        config.setSortNo(sortNo);
        config.setStatus(STATUS_NORMAL);
        formFieldConfigMapper.insert(config);
        return config;
    }

    /**
     * 插入某字段配置的选项列表
     *
     * @param fieldConfigId 字段配置ID
     * @param values 选项值列表（可空）
     */
    private void insertOptions(Long fieldConfigId, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            FormFieldOptionEntity option = new FormFieldOptionEntity();
            option.setFieldConfigId(fieldConfigId);
            option.setOptionLabel(values.get(index));
            option.setOptionValue(values.get(index));
            option.setSortNo(index);
            option.setStatus(STATUS_NORMAL);
            formFieldOptionMapper.insert(option);
        }
    }

    /**
     * 回填表单定义、物理表登记与表单数据的表维度列
     *
     * @param form 表单定义实体
     * @param table 表定义实体
     */
    private void backfill(FormDefinitionEntity form, FormTableDefinitionEntity table) {
        Integer version = versionOf(form);
        LambdaUpdateWrapper<FormDefinitionEntity> formUpdate = new LambdaUpdateWrapper<>();
        formUpdate.eq(FormDefinitionEntity::getId, form.getId());
        formUpdate.set(FormDefinitionEntity::getTableDefinitionId, table.getId());
        formDefinitionMapper.update(null, formUpdate);

        LambdaUpdateWrapper<FormTableRegistryEntity> registryUpdate = new LambdaUpdateWrapper<>();
        registryUpdate.eq(FormTableRegistryEntity::getDefinitionId, form.getId());
        registryUpdate.set(FormTableRegistryEntity::getTableDefinitionId, table.getId());
        registryUpdate.set(FormTableRegistryEntity::getTableCode, form.getCode());
        registryUpdate.set(FormTableRegistryEntity::getTableVersion, version);
        formTableRegistryMapper.update(null, registryUpdate);

        LambdaUpdateWrapper<FormDataEntity> dataUpdate = new LambdaUpdateWrapper<>();
        dataUpdate.eq(FormDataEntity::getDefinitionId, form.getId());
        dataUpdate.set(FormDataEntity::getTableDefinitionId, table.getId());
        dataUpdate.set(FormDataEntity::getTableCode, form.getCode());
        dataUpdate.set(FormDataEntity::getTableVersion, version);
        dataUpdate.set(FormDataEntity::getFormDefinitionId, form.getId());
        formDataMapper.update(null, dataUpdate);
    }

    /**
     * 解析 {@code schema_json} 中各字段的选项数组（key 为字段 key）
     *
     * @param schemaJson 表单结构定义 JSON
     * @return 字段选项映射；无选项或解析失败时返回空映射
     */
    private static Map<String, List<String>> parseOptions(String schemaJson) {
        Map<String, List<String>> result = new HashMap<>();
        JsonNode root;
        try {
            root = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            return result;
        }
        JsonNode fields = root.get(KEY_FIELDS);
        if (fields == null || !fields.isArray()) {
            return result;
        }
        for (JsonNode node : fields) {
            JsonNode options = node.get(KEY_OPTIONS);
            if (options != null && options.isArray() && !options.isEmpty()) {
                List<String> values = new ArrayList<>();
                for (JsonNode option : options) {
                    values.add(option.asText());
                }
                result.put(node.path("key").asText(), values);
            }
        }
        return result;
    }

    /**
     * 取表单定义版本（空则回落默认版本）
     *
     * @param form 表单定义实体
     * @return 表单定义版本
     */
    private static int versionOf(FormDefinitionEntity form) {
        return form.getVersion() == null ? DEFAULT_VERSION : form.getVersion();
    }
}
