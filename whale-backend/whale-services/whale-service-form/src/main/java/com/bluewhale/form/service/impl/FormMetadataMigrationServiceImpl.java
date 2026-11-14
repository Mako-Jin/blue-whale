package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
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
        LambdaQueryWrapper<FormDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(FormDefinitionPo::getTableDefinitionId);
        wrapper.isNotNull(FormDefinitionPo::getSchemaJson);
        List<FormDefinitionPo> forms = formDefinitionMapper.selectList(wrapper);
        int migrated = 0;
        for (FormDefinitionPo form : forms) {
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
    private boolean migrateOne(FormDefinitionPo form) {
        try {
            FormTableDdlGenerator.assertPublishableCode(form.getCode());
        } catch (BizException e) {
            return false;
        }
        List<FormField> fields = FormSchemaParser.parse(form.getSchemaJson());
        Map<String, List<String>> options = parseOptions(form.getSchemaJson());
        FormTableDefinitionPo table = ensureTableDefinition(form);
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
    private FormTableDefinitionPo ensureTableDefinition(FormDefinitionPo form) {
        LambdaQueryWrapper<FormTableDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableDefinitionPo::getTableCode, form.getCode());
        List<FormTableDefinitionPo> existing = formTableDefinitionMapper.selectList(wrapper);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        FormTableDefinitionPo table = new FormTableDefinitionPo();
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
    private void migrateFields(FormDefinitionPo form, FormTableDefinitionPo table, List<FormField> fields,
                               Map<String, List<String>> options) {
        Integer version = versionOf(form);
        for (int index = 0; index < fields.size(); index++) {
            FormField field = fields.get(index);
            FormColumnMetaPo column = insertColumn(table, version, field, index);
            FormFieldConfigPo config = insertFieldConfig(form, field, column.getId(), index);
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
    private FormColumnMetaPo insertColumn(FormTableDefinitionPo table, Integer version, FormField field, int sortNo) {
        FormColumnMetaPo column = new FormColumnMetaPo();
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
    private FormFieldConfigPo insertFieldConfig(FormDefinitionPo form, FormField field, Long columnMetaId, int sortNo) {
        FormFieldConfigPo config = new FormFieldConfigPo();
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
            FormFieldOptionPo option = new FormFieldOptionPo();
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
    private void backfill(FormDefinitionPo form, FormTableDefinitionPo table) {
        Integer version = versionOf(form);
        LambdaUpdateWrapper<FormDefinitionPo> formUpdate = new LambdaUpdateWrapper<>();
        formUpdate.eq(FormDefinitionPo::getId, form.getId());
        formUpdate.set(FormDefinitionPo::getTableDefinitionId, table.getId());
        formDefinitionMapper.update(null, formUpdate);

        LambdaUpdateWrapper<FormTableRegistryPo> registryUpdate = new LambdaUpdateWrapper<>();
        registryUpdate.eq(FormTableRegistryPo::getDefinitionId, form.getId());
        registryUpdate.set(FormTableRegistryPo::getTableDefinitionId, table.getId());
        registryUpdate.set(FormTableRegistryPo::getTableCode, form.getCode());
        registryUpdate.set(FormTableRegistryPo::getTableVersion, version);
        formTableRegistryMapper.update(null, registryUpdate);

        LambdaUpdateWrapper<FormDataPo> dataUpdate = new LambdaUpdateWrapper<>();
        dataUpdate.eq(FormDataPo::getDefinitionId, form.getId());
        dataUpdate.set(FormDataPo::getTableDefinitionId, table.getId());
        dataUpdate.set(FormDataPo::getTableCode, form.getCode());
        dataUpdate.set(FormDataPo::getTableVersion, version);
        dataUpdate.set(FormDataPo::getFormDefinitionId, form.getId());
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
    private static int versionOf(FormDefinitionPo form) {
        return form.getVersion() == null ? DEFAULT_VERSION : form.getVersion();
    }
}
