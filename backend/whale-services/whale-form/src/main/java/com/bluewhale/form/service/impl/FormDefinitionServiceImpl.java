package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDefinitionSaveRequest;
import com.bluewhale.form.dto.FormFieldConfigDTO;
import com.bluewhale.form.dto.FormFieldOptionDTO;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormFieldConfig;
import com.bluewhale.form.entity.FormFieldOption;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormFieldOptionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.service.FormDefinitionService;
import com.bluewhale.form.support.FormMetadataCompiler;
import com.bluewhale.form.support.FormWidgetType;
import com.bluewhale.form.vo.FormDefinitionVO;
import com.bluewhale.form.vo.FormFieldConfigVO;
import com.bluewhale.form.vo.FormFieldOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 表单定义服务实现（表单视图层）。
 *
 * <p>元数据模型（{@code tableDefinitionId != null}）：保存时持久化字段配置（含选项），
 * 并调用 {@link FormMetadataCompiler} 将其编译为 {@code schema_json} 发布快照写回；
 * 详情返回 {@code fields[]}。旧模型（{@code tableDefinitionId == null}）：沿用 Wave 5.27 的
 * {@code schema_json} 原路径，行为不变。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormDefinitionServiceImpl implements FormDefinitionService {

    /** 表单状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 表单版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 字段栅格宽度默认值（24 为整宽，12 为半宽） */
    private static final int DEFAULT_COL_SPAN = 24;

    /** 表单结构定义最大长度（字符数）：64KB */
    private static final int MAX_SCHEMA_LENGTH = 65536;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormFieldConfigMapper formFieldConfigMapper;

    private final FormFieldOptionMapper formFieldOptionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO create(FormDefinitionSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        FormDefinition entity = new FormDefinition();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setStatus(request.getStatus() == null ? STATUS_NORMAL : request.getStatus());
        entity.setRemark(request.getRemark());
        FormTableDefinition table = null;
        if (request.getTableDefinitionId() != null) {
            table = requireTableDefinition(request.getTableDefinitionId());
            entity.setTableDefinitionId(table.getId());
            entity.setSchemaJson(null);
        } else {
            validateSchemaSize(request.getSchemaJson());
            entity.setSchemaJson(request.getSchemaJson());
        }
        formDefinitionMapper.insert(entity);
        if (table != null) {
            List<FormFieldConfig> configs = persistFields(entity.getId(), request.getFields());
            entity.setSchemaJson(compileSnapshot(table, configs));
            formDefinitionMapper.updateById(entity);
        }
        return toDetailVo(entity, table == null ? null : listFields(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO update(Long id, FormDefinitionSaveRequest request) {
        FormDefinition entity = requireForm(id);
        if (!entity.getCode().equals(request.getCode())) {
            assertCodeUnique(request.getCode(), id);
            entity.setCode(request.getCode());
        }
        entity.setName(request.getName());
        if (request.getVersion() != null) {
            entity.setVersion(request.getVersion());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setRemark(request.getRemark());
        FormTableDefinition table = null;
        if (request.getTableDefinitionId() != null) {
            table = requireTableDefinition(request.getTableDefinitionId());
            entity.setTableDefinitionId(table.getId());
            entity.setSchemaJson(null);
        } else {
            entity.setTableDefinitionId(null);
            validateSchemaSize(request.getSchemaJson());
            entity.setSchemaJson(request.getSchemaJson());
        }
        formDefinitionMapper.updateById(entity);
        if (table != null) {
            List<FormFieldConfig> configs = replaceFields(entity.getId(), request.getFields());
            entity.setSchemaJson(compileSnapshot(table, configs));
            formDefinitionMapper.updateById(entity);
        } else {
            deleteFields(entity.getId());
        }
        return toDetailVo(entity, table == null ? null : listFields(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireForm(id);
        deleteFields(id);
        formDefinitionMapper.deleteById(id);
    }

    @Override
    public FormDefinitionVO getDetail(Long id) {
        FormDefinition entity = requireForm(id);
        return toDetailVo(entity, listFields(id));
    }

    @Override
    public PageResult<FormDefinitionVO> page(FormQueryRequest request) {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), FormDefinition::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), FormDefinition::getCode, request.getCode());
        wrapper.eq(request.getStatus() != null, FormDefinition::getStatus, request.getStatus());
        wrapper.orderByDesc(FormDefinition::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<FormDefinition> page = formDefinitionMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<FormDefinitionVO> records = page.getRecords().stream()
                .map(entity -> toVo(entity, null)).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 逐字段持久化字段配置及其选项，返回带主键的字段配置列表
     *
     * @param formId 表单定义ID
     * @param dtos 字段配置请求列表（可空）
     * @return 已落库的字段配置列表
     */
    private List<FormFieldConfig> persistFields(Long formId, List<FormFieldConfigDTO> dtos) {
        List<FormFieldConfig> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        for (FormFieldConfigDTO dto : dtos) {
            FormFieldConfig config = toConfig(dto, formId);
            formFieldConfigMapper.insert(config);
            persistOptions(config.getId(), dto.getOptions());
            result.add(config);
        }
        return result;
    }

    /**
     * 整体替换表单视图的字段配置与选项（旧配置逻辑删除后重建）
     *
     * @param formId 表单定义ID
     * @param dtos 字段配置请求列表
     * @return 新字段配置列表
     */
    private List<FormFieldConfig> replaceFields(Long formId, List<FormFieldConfigDTO> dtos) {
        deleteFields(formId);
        return persistFields(formId, dtos);
    }

    /**
     * 逻辑删除表单视图的全部字段配置及其选项
     *
     * @param formId 表单定义ID
     */
    private void deleteFields(Long formId) {
        LambdaQueryWrapper<FormFieldConfig> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(FormFieldConfig::getFormId, formId);
        List<FormFieldConfig> configs = formFieldConfigMapper.selectList(configWrapper);
        if (!configs.isEmpty()) {
            List<Long> configIds = configs.stream().map(FormFieldConfig::getId).toList();
            LambdaQueryWrapper<FormFieldOption> optionWrapper = new LambdaQueryWrapper<>();
            optionWrapper.in(FormFieldOption::getFieldConfigId, configIds);
            formFieldOptionMapper.delete(optionWrapper);
        }
        formFieldConfigMapper.delete(configWrapper);
    }

    /**
     * 持久化某字段配置的选项列表
     *
     * @param fieldConfigId 字段配置ID
     * @param dtos 选项请求列表（可空）
     */
    private void persistOptions(Long fieldConfigId, List<FormFieldOptionDTO> dtos) {
        if (dtos == null) {
            return;
        }
        for (FormFieldOptionDTO dto : dtos) {
            FormFieldOption option = new FormFieldOption();
            option.setFieldConfigId(fieldConfigId);
            option.setOptionLabel(dto.getOptionLabel());
            option.setOptionValue(dto.getOptionValue());
            option.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
            option.setStatus(STATUS_NORMAL);
            formFieldOptionMapper.insert(option);
        }
    }

    /**
     * 将字段配置（含选项）与列元数据编译为 {@code schema_json} 发布快照
     *
     * @param table 所属表定义（用于限定列归属）
     * @param configs 字段配置列表（需已带主键）
     * @return {@code schema_json} 文本
     * @throws BizException 控件类型非法、引用了非本表的列或无有效数据字段时抛出 {@code PARAM_ERROR}
     */
    private String compileSnapshot(FormTableDefinition table, List<FormFieldConfig> configs) {
        List<FormFieldConfig> active = configs.stream()
                .filter(config -> config.getShowInForm() == null || config.getShowInForm() == STATUS_NORMAL)
                .toList();
        Set<Long> columnIds = active.stream()
                .map(FormFieldConfig::getColumnMetaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (FormFieldConfig config : active) {
            if (config.getColumnMetaId() != null) {
                FormWidgetType.assertAllowed(config.getWidgetType(), config.getFieldLabel());
            }
        }
        Map<Long, FormColumnMeta> columns = loadColumns(table.getId(), columnIds);
        Map<Long, List<FormFieldOption>> options = loadOptions(configs);
        return FormMetadataCompiler.compile(active, columns, options);
    }

    /**
     * 按 ID 集合加载归属指定表定义的列元数据
     *
     * @param tableDefinitionId 表定义ID
     * @param ids 列元数据ID集合（可空）
     * @return 列元数据映射（key 为列元数据ID）
     */
    private Map<Long, FormColumnMeta> loadColumns(Long tableDefinitionId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<FormColumnMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMeta::getTableDefinitionId, tableDefinitionId);
        wrapper.in(FormColumnMeta::getId, ids);
        return formColumnMetaMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(FormColumnMeta::getId, Function.identity()));
    }

    /**
     * 按字段配置 ID 分组加载选项（按排序号升序）
     *
     * @param configs 字段配置列表
     * @return 选项映射（key 为字段配置ID）
     */
    private Map<Long, List<FormFieldOption>> loadOptions(List<FormFieldConfig> configs) {
        if (configs.isEmpty()) {
            return Map.of();
        }
        List<Long> configIds = configs.stream().map(FormFieldConfig::getId).toList();
        LambdaQueryWrapper<FormFieldOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FormFieldOption::getFieldConfigId, configIds);
        wrapper.orderByAsc(FormFieldOption::getSortNo);
        return formFieldOptionMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(FormFieldOption::getFieldConfigId));
    }

    /**
     * 查询表单视图的字段配置（含选项，按排序号升序）
     *
     * @param formId 表单定义ID
     * @return 字段配置视图对象列表
     */
    private List<FormFieldConfigVO> listFields(Long formId) {
        LambdaQueryWrapper<FormFieldConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormFieldConfig::getFormId, formId);
        wrapper.orderByAsc(FormFieldConfig::getSortNo);
        List<FormFieldConfig> configs = formFieldConfigMapper.selectList(wrapper);
        Map<Long, List<FormFieldOption>> optionMap = loadOptions(configs);
        return configs.stream().map(config -> toFieldVo(config, optionMap.get(config.getId()))).toList();
    }

    /**
     * 查询表单定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 表单定义ID
     * @return 表单定义实体
     */
    private FormDefinition requireForm(Long id) {
        FormDefinition entity = formDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 查询表定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param tableDefinitionId 表定义ID
     * @return 表定义实体
     */
    private FormTableDefinition requireTableDefinition(Long tableDefinitionId) {
        FormTableDefinition table = formTableDefinitionMapper.selectById(tableDefinitionId);
        if (table == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return table;
    }

    /**
     * 校验表单编码唯一性：命中已存在的编码则抛参数错误
     *
     * @param code 表单编码
     * @param excludeId 需排除的当前记录ID（更新时传入，新增传 {@code null}）
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinition::getCode, code);
        wrapper.ne(excludeId != null, FormDefinition::getId, excludeId);
        Long count = formDefinitionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw paramError("表单编码已存在");
        }
    }

    /**
     * 校验表单结构定义长度不超过 64KB
     *
     * @param schemaJson 表单结构定义JSON
     * @throws BizException 超长时抛出 {@code PARAM_ERROR}
     */
    private static void validateSchemaSize(String schemaJson) {
        if (schemaJson != null && schemaJson.length() > MAX_SCHEMA_LENGTH) {
            throw paramError("表单结构定义长度不能超过 65536 字符");
        }
    }

    /**
     * 字段配置请求 → 实体（补齐默认值）
     *
     * @param dto 字段配置请求
     * @param formId 表单定义ID
     * @return 字段配置实体
     */
    private static FormFieldConfig toConfig(FormFieldConfigDTO dto, Long formId) {
        FormFieldConfig config = new FormFieldConfig();
        config.setFormId(formId);
        config.setColumnMetaId(dto.getColumnMetaId());
        config.setFieldLabel(dto.getFieldLabel());
        config.setWidgetType(dto.getWidgetType());
        config.setShowInList(dto.getShowInList() == null ? STATUS_NORMAL : dto.getShowInList());
        config.setShowInForm(dto.getShowInForm() == null ? STATUS_NORMAL : dto.getShowInForm());
        config.setEditable(dto.getEditable() == null ? STATUS_NORMAL : dto.getEditable());
        config.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        config.setColSpan(dto.getColSpan() == null ? DEFAULT_COL_SPAN : dto.getColSpan());
        config.setPlaceholder(dto.getPlaceholder());
        config.setDefaultValue(dto.getDefaultValue());
        config.setValidateJson(dto.getValidateJson());
        config.setRemark(dto.getRemark());
        config.setStatus(dto.getStatus() == null ? STATUS_NORMAL : dto.getStatus());
        return config;
    }

    /**
     * 字段配置实体 + 选项 → 视图对象
     *
     * @param config 字段配置实体
     * @param options 选项实体列表（可空）
     * @return 字段配置视图对象
     */
    private static FormFieldConfigVO toFieldVo(FormFieldConfig config, List<FormFieldOption> options) {
        FormFieldConfigVO vo = new FormFieldConfigVO();
        BeanUtils.copyProperties(config, vo);
        if (options != null) {
            vo.setOptions(options.stream().map(FormDefinitionServiceImpl::toOptionVo).toList());
        }
        return vo;
    }

    /**
     * 选项实体 → 视图对象
     *
     * @param option 选项实体
     * @return 选项视图对象
     */
    private static FormFieldOptionVO toOptionVo(FormFieldOption option) {
        FormFieldOptionVO vo = new FormFieldOptionVO();
        BeanUtils.copyProperties(option, vo);
        return vo;
    }

    /**
     * 表单定义实体 + 字段配置 → 视图对象
     *
     * @param entity 表单定义实体
     * @param fields 字段配置视图对象列表（分页/旧模型可传 {@code null}）
     * @return 表单定义视图对象
     */
    private static FormDefinitionVO toVo(FormDefinition entity, List<FormFieldConfigVO> fields) {
        FormDefinitionVO vo = new FormDefinitionVO();
        BeanUtils.copyProperties(entity, vo);
        if (fields != null) {
            vo.setFields(fields);
        }
        return vo;
    }

    /**
     * 表单定义实体 + 字段配置 → 视图对象（详情场景，回填关联列信息）
     *
     * @param entity 表单定义实体
     * @param fields 字段配置视图对象列表（可空）
     * @return 表单定义视图对象（字段已回填 {@code columnCode/dataType/required/maxLength}）
     */
    private FormDefinitionVO toDetailVo(FormDefinition entity, List<FormFieldConfigVO> fields) {
        FormDefinitionVO vo = toVo(entity, fields);
        enrichColumnInfo(vo.getFields(), entity.getTableDefinitionId());
        return vo;
    }

    /**
     * 回填字段配置的关联列信息（列编码 / 存储类型 / 必填 / 长度），供设计器一次请求还原画布
     *
     * <p>按 {@code tableDefinitionId} 一次查全列建 Map，无 N+1；纯布局字段（{@code columnMetaId == null}）
     * 或旧模型（{@code tableDefinitionId == null}）直接跳过。</p>
     *
     * @param fields 字段配置视图对象列表（可空）
     * @param tableDefinitionId 所属表定义ID（可空）
     */
    private void enrichColumnInfo(List<FormFieldConfigVO> fields, Long tableDefinitionId) {
        if (fields == null || fields.isEmpty() || tableDefinitionId == null) {
            return;
        }
        LambdaQueryWrapper<FormColumnMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMeta::getTableDefinitionId, tableDefinitionId);
        Map<Long, FormColumnMeta> columnMap = formColumnMetaMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(FormColumnMeta::getId, Function.identity()));
        for (FormFieldConfigVO field : fields) {
            FormColumnMeta column = field.getColumnMetaId() == null ? null : columnMap.get(field.getColumnMetaId());
            if (column == null) {
                continue;
            }
            field.setColumnCode(column.getColumnCode());
            field.setDataType(column.getDataType());
            field.setRequired(column.getRequired());
            field.setMaxLength(column.getLength());
        }
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误消息
     * @return 参数错误业务异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
