package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.converter.FormConverter;
import com.bluewhale.form.model.request.FormDefinitionCopyRequest;
import com.bluewhale.form.model.request.FormDefinitionSaveRequest;
import com.bluewhale.form.model.dto.FormFieldConfigDTO;
import com.bluewhale.form.model.dto.FormFieldOptionDTO;
import com.bluewhale.form.model.request.FormQueryRequest;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormDefinitionVersionPo;
import com.bluewhale.form.po.FormFieldConfigPo;
import com.bluewhale.form.po.FormFieldOptionPo;
import com.bluewhale.form.po.FormTableDefinitionPo;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormDefinitionVersionMapper;
import com.bluewhale.form.mapper.FormFieldConfigMapper;
import com.bluewhale.form.mapper.FormFieldOptionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.service.FormDefinitionService;
import com.bluewhale.form.support.FormJson;
import com.bluewhale.form.support.FormMetadataCache;
import com.bluewhale.form.support.FormMetadataCompiler;
import com.bluewhale.form.support.FormWidgetType;
import com.bluewhale.form.model.vo.FormDefinitionVO;
import com.bluewhale.form.model.vo.FormDefinitionVersionVO;
import com.bluewhale.form.model.vo.FormFieldConfigVO;
import com.bluewhale.form.model.vo.FormFieldOptionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
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

    /** 复制编码后缀 */
    private static final String COPY_CODE_SUFFIX = "_copy";

    /** 复制名称后缀 */
    private static final String COPY_NAME_SUFFIX = "（副本）";

    /** 复制编码的最大尝试次数 */
    private static final int MAX_COPY_ATTEMPTS = 100;

    /** 表单编码最大长度（与物理表名可发布规则对齐） */
    private static final int MAX_CODE_LENGTH = 40;

    /** 表单名称最大长度 */
    private static final int MAX_NAME_LENGTH = 128;

    /** 标签格式错误提示 */
    private static final String TAGS_FORMAT_MESSAGE = "标签格式应为 JSON 字符串数组";

    /** 版本快照 JSON 序列化器 */
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormFieldConfigMapper formFieldConfigMapper;

    private final FormFieldOptionMapper formFieldOptionMapper;

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormDefinitionVersionMapper formDefinitionVersionMapper;

    private final FormMetadataCache metadataCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO create(FormDefinitionSaveRequest request) {
        assertStructureSource(request);
        assertCodeUnique(request.getCode(), null);
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setStatus(request.getStatus() == null ? STATUS_NORMAL : request.getStatus());
        entity.setRemark(request.getRemark());
        entity.setCategory(normalizeCategory(request.getCategory()));
        entity.setTags(normalizeTags(request.getTags()));
        FormTableDefinitionPo table = null;
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
            List<FormFieldConfigPo> configs = persistFields(entity.getId(), request.getFields());
            entity.setSchemaJson(compileSnapshot(table, configs));
            formDefinitionMapper.updateById(entity);
        }
        writeVersionSnapshot(entity);
        metadataCache.invalidateDefinition(entity.getId(), entity.getCode());
        return toDetailVo(entity, table == null ? null : listFields(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO update(Long id, FormDefinitionSaveRequest request) {
        assertStructureSource(request);
        FormDefinitionPo entity = requireForm(id);
        String originalCode = entity.getCode();
        int expectedVersion = entity.getVersion() == null ? DEFAULT_VERSION : entity.getVersion();
        if (!entity.getCode().equals(request.getCode())) {
            assertCodeUnique(request.getCode(), id);
            entity.setCode(request.getCode());
        }
        entity.setName(request.getName());
        if (request.getVersion() != null) {
            if (request.getVersion() < expectedVersion) {
                throw paramError("版本号只能递增，不能从 " + expectedVersion + " 回退到 " + request.getVersion());
            }
            entity.setVersion(request.getVersion());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setRemark(request.getRemark());
        entity.setCategory(normalizeCategory(request.getCategory()));
        entity.setTags(normalizeTags(request.getTags()));
        FormTableDefinitionPo table = null;
        if (request.getTableDefinitionId() != null) {
            table = requireTableDefinition(request.getTableDefinitionId());
            entity.setTableDefinitionId(table.getId());
            entity.setSchemaJson(null);
        } else {
            entity.setTableDefinitionId(null);
            validateSchemaSize(request.getSchemaJson());
            entity.setSchemaJson(request.getSchemaJson());
        }
        if (table != null) {
            List<FormFieldConfigPo> configs = replaceFields(entity.getId(), request.getFields());
            entity.setSchemaJson(compileSnapshot(table, configs));
        } else {
            deleteFields(entity.getId());
        }
        updateWithVersionGuard(entity, expectedVersion);
        writeVersionSnapshot(entity);
        metadataCache.invalidateDefinition(entity.getId(), originalCode);
        metadataCache.invalidateDefinition(entity.getId(), entity.getCode());
        return toDetailVo(entity, table == null ? null : listFields(entity.getId()));
    }

    /**
     * 按读取时的版本号做条件更新（CAS），防止并发发布/回滚/编辑相互覆盖
     *
     * @param entity 待更新实体（其 version 字段可能已被业务改写）
     * @param expectedVersion 读取时的版本号，作为更新条件
     * @throws BizException 版本已被他人修改时抛出 {@code PARAM_ERROR}
     */
    private void updateWithVersionGuard(FormDefinitionPo entity, int expectedVersion) {
        LambdaUpdateWrapper<FormDefinitionPo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FormDefinitionPo::getId, entity.getId());
        wrapper.eq(FormDefinitionPo::getVersion, expectedVersion);
        int rows = formDefinitionMapper.update(entity, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单已被并发修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FormDefinitionPo entity = requireForm(id);
        deleteFields(id);
        formDefinitionMapper.deleteById(id);
        metadataCache.invalidateDefinition(id, entity.getCode());
    }

    @Override
    public FormDefinitionVO getDetail(Long id) {
        FormDefinitionPo entity = requireForm(id);
        return toDetailVo(entity, listFields(id));
    }

    @Override
    public PageResult<FormDefinitionVO> page(FormQueryRequest request) {
        LambdaQueryWrapper<FormDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), FormDefinitionPo::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), FormDefinitionPo::getCode, request.getCode());
        wrapper.eq(request.getStatus() != null, FormDefinitionPo::getStatus, request.getStatus());
        String category = normalizeCategory(request.getCategory());
        wrapper.eq(category != null, FormDefinitionPo::getCategory, category);
        wrapper.orderByDesc(FormDefinitionPo::getCreateTime);
        Page<FormDefinitionPo> page = formDefinitionMapper.selectPage(request.toPage(), wrapper);
        List<FormDefinitionVO> records = page.getRecords().stream()
                .map(FormConverter.INSTANCE::toDefinitionSummaryVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO copy(Long id, FormDefinitionCopyRequest request) {
        FormDefinitionPo source = requireForm(id);
        FormDefinitionPo entity = new FormDefinitionPo();
        entity.setCode(resolveCopyCode(source.getCode(), request));
        entity.setName(resolveCopyName(source.getName(), request));
        entity.setTableDefinitionId(source.getTableDefinitionId());
        entity.setSchemaJson(source.getSchemaJson());
        entity.setVersion(DEFAULT_VERSION);
        entity.setStatus(STATUS_NORMAL);
        entity.setRemark(source.getRemark());
        formDefinitionMapper.insert(entity);
        List<FormFieldConfigPo> configs = copyFields(entity.getId(), source.getId());
        if (source.getTableDefinitionId() != null) {
            FormTableDefinitionPo table = requireTableDefinition(source.getTableDefinitionId());
            entity.setSchemaJson(compileSnapshot(table, configs));
            formDefinitionMapper.updateById(entity);
        }
        writeVersionSnapshot(entity);
        metadataCache.invalidateDefinition(entity.getId(), entity.getCode());
        return toDetailVo(entity, listFields(entity.getId()));
    }

    @Override
    public List<FormDefinitionVersionVO> listVersions(Long id) {
        requireForm(id);
        LambdaQueryWrapper<FormDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionVersionPo::getDefinitionId, id);
        wrapper.orderByDesc(FormDefinitionVersionPo::getSnapshotSeq);
        wrapper.orderByDesc(FormDefinitionVersionPo::getId);
        return formDefinitionVersionMapper.selectList(wrapper).stream()
                .map(FormDefinitionServiceImpl::toVersionVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDefinitionVO rollback(Long id, Integer version) {
        FormDefinitionPo entity = requireForm(id);
        String originalCode = entity.getCode();
        FormDefinitionVersionPo snapshot = findSnapshot(id, version);
        JsonNode root = parseSnapshot(snapshot.getSnapshotJson());
        JsonNode definition = root.path("definition");
        restoreDefinition(entity, definition);
        int currentVersion = entity.getVersion() == null ? DEFAULT_VERSION : entity.getVersion();
        entity.setVersion(nextDefinitionVersion(id, currentVersion));
        deleteFields(id);
        List<FormFieldConfigPo> configs = restoreFields(id, root.path("fields"));
        if (entity.getTableDefinitionId() != null) {
            entity.setSchemaJson(recompile(entity.getTableDefinitionId(), configs, entity.getSchemaJson()));
        }
        updateWithVersionGuard(entity, currentVersion);
        writeVersionSnapshot(entity);
        metadataCache.invalidateDefinition(entity.getId(), originalCode);
        metadataCache.invalidateDefinition(entity.getId(), entity.getCode());
        return toDetailVo(entity, listFields(id));
    }

    /**
     * 按复制请求解析新表单编码：显式编码校验唯一，否则生成 {@code _copy}、{@code _copy2}…
     *
     * @param sourceCode 源表单编码
     * @param request 复制请求（可空）
     * @return 唯一的新表单编码
     * @throws BizException 显式编码已存在或无法生成唯一编码时抛出 {@code PARAM_ERROR}
     */
    private String resolveCopyCode(String sourceCode, FormDefinitionCopyRequest request) {
        if (request != null && StringUtils.hasText(request.getCode())) {
            assertCodeUnique(request.getCode(), null);
            return request.getCode();
        }
        for (int i = 1; i <= MAX_COPY_ATTEMPTS; i++) {
            String suffix = i == 1 ? COPY_CODE_SUFFIX : COPY_CODE_SUFFIX + i;
            String candidate = truncateCode(sourceCode, MAX_CODE_LENGTH - suffix.length()) + suffix;
            if (isCodeAvailable(candidate)) {
                return candidate;
            }
        }
        throw paramError("无法生成唯一表单编码");
    }

    /**
     * 按最大长度截断编码（复制编码需保证不超过物理表名可发布长度）
     *
     * @param code 源编码
     * @param maxLength 允许的最大长度
     * @return 截断后的编码
     */
    private static String truncateCode(String code, int maxLength) {
        return code.length() <= maxLength ? code : code.substring(0, maxLength);
    }

    /**
     * 按复制请求解析新表单名称：显式名称优先，否则生成 {@code <源name>（副本）} 并截断超长
     *
     * @param sourceName 源表单名称
     * @param request 复制请求（可空）
     * @return 新表单名称
     */
    private static String resolveCopyName(String sourceName, FormDefinitionCopyRequest request) {
        if (request != null && StringUtils.hasText(request.getName())) {
            return request.getName();
        }
        String name = sourceName + COPY_NAME_SUFFIX;
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * 判断表单编码是否未被占用
     *
     * @param code 表单编码
     * @return 未被占用返回 {@code true}
     */
    private boolean isCodeAvailable(String code) {
        LambdaQueryWrapper<FormDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionPo::getCode, code);
        Long count = formDefinitionMapper.selectCount(wrapper);
        return count == null || count == 0;
    }

    /**
     * 复制源表单的字段配置与选项（按排序号顺序，生成新主键）
     *
     * @param targetFormId 目标表单定义ID
     * @param sourceFormId 源表单定义ID
     * @return 新字段配置列表
     */
    private List<FormFieldConfigPo> copyFields(Long targetFormId, Long sourceFormId) {
        List<FormFieldConfigPo> sources = listConfigs(sourceFormId);
        Map<Long, List<FormFieldOptionPo>> optionMap = loadOptions(sources);
        List<FormFieldConfigPo> result = new ArrayList<>();
        for (FormFieldConfigPo source : sources) {
            FormFieldConfigPo config = FormConverter.INSTANCE.toNewConfig(source);
            config.setId(null);
            config.setFormId(targetFormId);
            config.setCreateTime(null);
            config.setUpdateTime(null);
            config.setDeleted(null);
            formFieldConfigMapper.insert(config);
            persistRawOptions(config.getId(), optionMap.get(source.getId()));
            result.add(config);
        }
        return result;
    }

    /**
     * 查询表单定义下全部字段配置（按排序号升序）
     *
     * @param formId 表单定义ID
     * @return 字段配置列表
     */
    private List<FormFieldConfigPo> listConfigs(Long formId) {
        LambdaQueryWrapper<FormFieldConfigPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormFieldConfigPo::getFormId, formId);
        wrapper.orderByAsc(FormFieldConfigPo::getSortNo);
        return formFieldConfigMapper.selectList(wrapper);
    }

    /**
     * 按源选项实体复制选项并落库
     *
     * @param fieldConfigId 目标字段配置ID
     * @param options 源选项实体列表（可空）
     */
    private void persistRawOptions(Long fieldConfigId, List<FormFieldOptionPo> options) {
        if (options == null) {
            return;
        }
        for (FormFieldOptionPo source : options) {
            FormFieldOptionPo option = new FormFieldOptionPo();
            option.setFieldConfigId(fieldConfigId);
            option.setOptionLabel(source.getOptionLabel());
            option.setOptionValue(source.getOptionValue());
            option.setSortNo(source.getSortNo() == null ? 0 : source.getSortNo());
            option.setStatus(source.getStatus() == null ? STATUS_NORMAL : source.getStatus());
            formFieldOptionMapper.insert(option);
        }
    }

    /**
     * 逐字段持久化字段配置及其选项，返回带主键的字段配置列表
     *
     * @param formId 表单定义ID
     * @param dtos 字段配置请求列表（可空）
     * @return 已落库的字段配置列表
     */
    private List<FormFieldConfigPo> persistFields(Long formId, List<FormFieldConfigDTO> dtos) {
        List<FormFieldConfigPo> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        for (FormFieldConfigDTO dto : dtos) {
            FormFieldConfigPo config = toConfig(dto, formId);
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
    private List<FormFieldConfigPo> replaceFields(Long formId, List<FormFieldConfigDTO> dtos) {
        deleteFields(formId);
        return persistFields(formId, dtos);
    }

    /**
     * 逻辑删除表单视图的全部字段配置及其选项
     *
     * @param formId 表单定义ID
     */
    private void deleteFields(Long formId) {
        LambdaQueryWrapper<FormFieldConfigPo> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(FormFieldConfigPo::getFormId, formId);
        List<FormFieldConfigPo> configs = formFieldConfigMapper.selectList(configWrapper);
        if (!configs.isEmpty()) {
            List<Long> configIds = configs.stream().map(FormFieldConfigPo::getId).toList();
            LambdaQueryWrapper<FormFieldOptionPo> optionWrapper = new LambdaQueryWrapper<>();
            optionWrapper.in(FormFieldOptionPo::getFieldConfigId, configIds);
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
            FormFieldOptionPo option = new FormFieldOptionPo();
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
    private String compileSnapshot(FormTableDefinitionPo table, List<FormFieldConfigPo> configs) {
        List<FormFieldConfigPo> active = configs.stream()
                .filter(config -> config.getShowInForm() == null || config.getShowInForm() == STATUS_NORMAL)
                .toList();
        Set<Long> columnIds = active.stream()
                .map(FormFieldConfigPo::getColumnMetaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (FormFieldConfigPo config : active) {
            if (config.getColumnMetaId() != null) {
                FormWidgetType.assertAllowed(config.getWidgetType(), config.getFieldLabel());
            }
        }
        Map<Long, FormColumnMetaPo> columns = loadColumns(table.getId(), columnIds);
        Map<Long, List<FormFieldOptionPo>> options = loadOptions(configs);
        return FormMetadataCompiler.compile(active, columns, options);
    }

    /**
     * 按 ID 集合加载归属指定表定义的列元数据
     *
     * @param tableDefinitionId 表定义ID
     * @param ids 列元数据ID集合（可空）
     * @return 列元数据映射（key 为列元数据ID）
     */
    private Map<Long, FormColumnMetaPo> loadColumns(Long tableDefinitionId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaPo::getTableDefinitionId, tableDefinitionId);
        wrapper.in(FormColumnMetaPo::getId, ids);
        return formColumnMetaMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(FormColumnMetaPo::getId, Function.identity()));
    }

    /**
     * 按字段配置 ID 分组加载选项（按排序号升序）
     *
     * @param configs 字段配置列表
     * @return 选项映射（key 为字段配置ID）
     */
    private Map<Long, List<FormFieldOptionPo>> loadOptions(List<FormFieldConfigPo> configs) {
        if (configs.isEmpty()) {
            return Map.of();
        }
        List<Long> configIds = configs.stream().map(FormFieldConfigPo::getId).toList();
        LambdaQueryWrapper<FormFieldOptionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FormFieldOptionPo::getFieldConfigId, configIds);
        wrapper.orderByAsc(FormFieldOptionPo::getSortNo);
        return formFieldOptionMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(FormFieldOptionPo::getFieldConfigId));
    }

    /**
     * 查询表单视图的字段配置（含选项，按排序号升序）
     *
     * @param formId 表单定义ID
     * @return 字段配置视图对象列表
     */
    private List<FormFieldConfigVO> listFields(Long formId) {
        LambdaQueryWrapper<FormFieldConfigPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormFieldConfigPo::getFormId, formId);
        wrapper.orderByAsc(FormFieldConfigPo::getSortNo);
        List<FormFieldConfigPo> configs = formFieldConfigMapper.selectList(wrapper);
        Map<Long, List<FormFieldOptionPo>> optionMap = loadOptions(configs);
        return configs.stream().map(config -> toFieldVo(config, optionMap.get(config.getId()))).toList();
    }

    /**
     * 查询表单定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 表单定义ID
     * @return 表单定义实体
     */
    private FormDefinitionPo requireForm(Long id) {
        FormDefinitionPo entity = formDefinitionMapper.selectById(id);
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
    private FormTableDefinitionPo requireTableDefinition(Long tableDefinitionId) {
        FormTableDefinitionPo table = formTableDefinitionMapper.selectById(tableDefinitionId);
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
        LambdaQueryWrapper<FormDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionPo::getCode, code);
        wrapper.ne(excludeId != null, FormDefinitionPo::getId, excludeId);
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
     * 校验结构来源互斥：元数据模型以 {@code fields} 为准，旧模型以 {@code schemaJson} 为准，
     * 同时提交会产生两套互相覆盖的结构来源。
     *
     * @param request 表单保存请求
     * @throws BizException 同时提交 {@code fields} 与 {@code schemaJson} 时抛出 {@code PARAM_ERROR}
     */
    private static void assertStructureSource(FormDefinitionSaveRequest request) {
        boolean hasFields = request.getFields() != null && !request.getFields().isEmpty();
        boolean hasSchema = request.getSchemaJson() != null && !request.getSchemaJson().isBlank();
        if (hasFields && hasSchema) {
            throw paramError("fields 与 schemaJson 不能同时提交");
        }
    }

    /**
     * 规整表单分类：去首尾空白，空白视为不分类
     *
     * @param category 原始分类
     * @return 规整后的分类；空白时为 {@code null}
     */
    private static String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : null;
    }

    /**
     * 归一化字段级角色编码：仅接受 JSON 字符串数组，去重后回写；空数组归一为 {@code null}（不限制）
     *
     * @param raw 原始角色 JSON 文本
     * @param label 业务标签（用于错误提示）
     * @return 归一化后的 JSON 数组文本；未配置返回 {@code null}
     * @throws BizException 非 JSON 字符串数组或角色编码过长时抛出 {@code PARAM_ERROR}
     */
    private static String normalizeRoleCodes(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        FormJson.assertDepth(text, label);
        try {
            JsonNode node = SNAPSHOT_MAPPER.readTree(text);
            if (node == null || !node.isArray()) {
                throw paramError(label + "格式应为 JSON 字符串数组");
            }
            Set<String> codes = new LinkedHashSet<>();
            for (JsonNode item : node) {
                if (!item.isTextual() || !StringUtils.hasText(item.asText())) {
                    throw paramError(label + "中的角色编码必须为非空字符串");
                }
                String code = item.asText().trim();
                if (code.length() > 64) {
                    throw paramError(label + "中的角色编码不能超过 64 字符");
                }
                codes.add(code);
            }
            return codes.isEmpty() ? null : SNAPSHOT_MAPPER.writeValueAsString(codes);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw paramError(label + "格式应为 JSON 字符串数组");
        }
    }

    /**
     * 校验并规整标签：必须为 JSON 字符串数组文本
     *
     * @param tags 原始标签文本（可空）
     * @return 规整后的标签文本；空白时为 {@code null}
     * @throws BizException 无法解析为 JSON 字符串数组时抛出 {@code PARAM_ERROR}
     */
    private static String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        String text = tags.trim();
        FormJson.assertDepth(text, "标签");
        try {
            JsonNode node = SNAPSHOT_MAPPER.readTree(text);
            if (node == null || !node.isArray()) {
                throw paramError(TAGS_FORMAT_MESSAGE);
            }
            for (JsonNode tag : node) {
                if (!tag.isTextual()) {
                    throw paramError(TAGS_FORMAT_MESSAGE);
                }
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw paramError(TAGS_FORMAT_MESSAGE);
        }
    }

    /**
     * 字段配置请求 → 实体（补齐默认值）
     *
     * @param dto 字段配置请求
     * @param formId 表单定义ID
     * @return 字段配置实体
     */
    private static FormFieldConfigPo toConfig(FormFieldConfigDTO dto, Long formId) {
        FormFieldConfigPo config = new FormFieldConfigPo();
        config.setFormId(formId);
        config.setColumnMetaId(dto.getColumnMetaId());
        config.setFieldLabel(dto.getFieldLabel());
        config.setWidgetType(dto.getWidgetType());
        config.setShowInList(dto.getShowInList() == null ? STATUS_NORMAL : dto.getShowInList());
        config.setShowInForm(dto.getShowInForm() == null ? STATUS_NORMAL : dto.getShowInForm());
        config.setEditable(dto.getEditable() == null ? STATUS_NORMAL : dto.getEditable());
        config.setVisibleRoles(normalizeRoleCodes(dto.getVisibleRoles(), "可见角色"));
        config.setEditableRoles(normalizeRoleCodes(dto.getEditableRoles(), "可编辑角色"));
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
    private static FormFieldConfigVO toFieldVo(FormFieldConfigPo config, List<FormFieldOptionPo> options) {
        FormFieldConfigVO vo = FormConverter.INSTANCE.toFieldConfigVo(config);
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
    private static FormFieldOptionVO toOptionVo(FormFieldOptionPo option) {
        return FormConverter.INSTANCE.toOptionVo(option);
    }

    /**
     * 表单定义实体 + 字段配置 → 视图对象
     *
     * @param entity 表单定义实体
     * @param fields 字段配置视图对象列表（分页/旧模型可传 {@code null}）
     * @return 表单定义视图对象
     */
    private static FormDefinitionVO toVo(FormDefinitionPo entity, List<FormFieldConfigVO> fields) {
        FormDefinitionVO vo = FormConverter.INSTANCE.toDefinitionVo(entity);
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
    private FormDefinitionVO toDetailVo(FormDefinitionPo entity, List<FormFieldConfigVO> fields) {
        FormDefinitionVO vo = toVo(entity, fields);
        enrichColumnInfo(vo.getFields(), entity.getTableDefinitionId());
        return vo;
    }

    /**
     * 回填字段配置的关联列信息（列编码 / 存储类型 / 必填 / 长度），供设计器一次请求还原画布
     *
     * <p>按字段引用的列ID一次 {@code IN} 查询建 Map，无 N+1 且不随表定义列数放大；
     * 纯布局字段（{@code columnMetaId == null}）或旧模型（{@code tableDefinitionId == null}）直接跳过。</p>
     *
     * @param fields 字段配置视图对象列表（可空）
     * @param tableDefinitionId 所属表定义ID（可空）
     */
    private void enrichColumnInfo(List<FormFieldConfigVO> fields, Long tableDefinitionId) {
        if (fields == null || fields.isEmpty() || tableDefinitionId == null) {
            return;
        }
        Set<Long> columnIds = fields.stream()
                .map(FormFieldConfigVO::getColumnMetaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (columnIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<FormColumnMetaPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaPo::getTableDefinitionId, tableDefinitionId);
        wrapper.in(FormColumnMetaPo::getId, columnIds);
        Map<Long, FormColumnMetaPo> columnMap = formColumnMetaMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(FormColumnMetaPo::getId, Function.identity()));
        for (FormFieldConfigVO field : fields) {
            FormColumnMetaPo column = field.getColumnMetaId() == null ? null : columnMap.get(field.getColumnMetaId());
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
     * 查询指定版本快照（同版本多条取 ID 最大一条），不存在则抛出 {@code NOT_FOUND}
     *
     * @param definitionId 表单定义ID
     * @param version 版本号
     * @return 版本快照实体
     */
    private FormDefinitionVersionPo findSnapshot(Long definitionId, Integer version) {
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        LambdaQueryWrapper<FormDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.eq(FormDefinitionVersionPo::getVersion, version);
        wrapper.orderByDesc(FormDefinitionVersionPo::getId);
        List<FormDefinitionVersionPo> snapshots = formDefinitionVersionMapper.selectList(wrapper);
        if (snapshots.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return snapshots.get(0);
    }

    /**
     * 解析版本快照 JSON
     *
     * @param snapshotJson 快照 JSON 文本
     * @return 快照根节点
     */
    private static JsonNode parseSnapshot(String snapshotJson) {
        try {
            JsonNode root = SNAPSHOT_MAPPER.readTree(snapshotJson);
            if (root == null || !root.isObject()) {
                throw paramError("版本快照格式非法");
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw paramError("版本快照格式非法");
        }
    }

    /**
     * 用快照中的定义信息恢复表单定义（编码冲突时抛参数错误）
     *
     * @param entity 待恢复的表单定义实体
     * @param definition 快照的定义节点
     */
    private void restoreDefinition(FormDefinitionPo entity, JsonNode definition) {
        String code = textOrNull(definition, "code");
        if (StringUtils.hasText(code) && !Objects.equals(code, entity.getCode())) {
            assertCodeUnique(code, null);
            entity.setCode(code);
        }
        entity.setName(textOrNull(definition, "name"));
        entity.setTableDefinitionId(longOrNull(definition, "tableDefinitionId"));
        entity.setSchemaJson(textOrNull(definition, "schemaJson"));
        entity.setRemark(textOrNull(definition, "remark"));
        Integer status = intOrNull(definition, "status");
        entity.setStatus(status == null ? STATUS_NORMAL : status);
    }

    /**
     * 按快照字段数组重建字段配置与选项（新主键）
     *
     * @param formId 表单定义ID
     * @param fields 快照字段数组
     * @return 新字段配置列表
     */
    private List<FormFieldConfigPo> restoreFields(Long formId, JsonNode fields) {
        List<FormFieldConfigPo> result = new ArrayList<>();
        if (fields == null || !fields.isArray()) {
            return result;
        }
        for (JsonNode node : fields) {
            FormFieldConfigPo config = snapshotToConfig(formId, node);
            formFieldConfigMapper.insert(config);
            restoreOptions(config.getId(), node.path("options"));
            result.add(config);
        }
        return result;
    }

    /**
     * 快照字段节点 → 字段配置实体
     *
     * @param formId 表单定义ID
     * @param node 快照字段节点
     * @return 字段配置实体
     */
    private static FormFieldConfigPo snapshotToConfig(Long formId, JsonNode node) {
        FormFieldConfigPo config = new FormFieldConfigPo();
        config.setFormId(formId);
        config.setColumnMetaId(longOrNull(node, "columnMetaId"));
        config.setFieldLabel(textOrNull(node, "fieldLabel"));
        config.setWidgetType(textOrNull(node, "widgetType"));
        config.setShowInList(intOrNull(node, "showInList"));
        config.setShowInForm(intOrNull(node, "showInForm"));
        config.setEditable(intOrNull(node, "editable"));
        config.setSortNo(intOrNull(node, "sortNo"));
        config.setColSpan(intOrNull(node, "colSpan"));
        config.setPlaceholder(textOrNull(node, "placeholder"));
        config.setDefaultValue(textOrNull(node, "defaultValue"));
        config.setValidateJson(textOrNull(node, "validateJson"));
        config.setRemark(textOrNull(node, "remark"));
        config.setStatus(intOrNull(node, "status"));
        return config;
    }

    /**
     * 按快照选项数组重建字段选项
     *
     * @param fieldConfigId 字段配置ID
     * @param options 快照选项数组
     */
    private void restoreOptions(Long fieldConfigId, JsonNode options) {
        if (options == null || !options.isArray()) {
            return;
        }
        for (JsonNode node : options) {
            FormFieldOptionPo option = new FormFieldOptionPo();
            option.setFieldConfigId(fieldConfigId);
            option.setOptionLabel(textOrNull(node, "optionLabel"));
            option.setOptionValue(textOrNull(node, "optionValue"));
            Integer sortNo = intOrNull(node, "sortNo");
            option.setSortNo(sortNo == null ? 0 : sortNo);
            Integer status = intOrNull(node, "status");
            option.setStatus(status == null ? STATUS_NORMAL : status);
            formFieldOptionMapper.insert(option);
        }
    }

    /**
     * 元数据模型下重新编译 {@code schema_json}；编译失败且快照 schema 非空时沿用快照
     *
     * @param tableDefinitionId 表定义ID
     * @param configs 重建后的字段配置
     * @param snapshotSchema 快照中的 schema JSON（可空）
     * @return {@code schema_json} 文本
     */
    private String recompile(Long tableDefinitionId, List<FormFieldConfigPo> configs, String snapshotSchema) {
        try {
            return compileSnapshot(requireTableDefinition(tableDefinitionId), configs);
        } catch (BizException e) {
            if (StringUtils.hasText(snapshotSchema)) {
                return snapshotSchema;
            }
            throw e;
        }
    }

    /**
     * 追加一条当前版本的快照（append-only，不覆盖历史）
     *
     * <p>版本号表示发布版本，同一版本可以被多次编辑；每次保存都按 {@code snapshot_seq}
     * 追加一条，保留完整编辑轨迹。回滚产生的版本号由
     * {@link #nextDefinitionVersion(Long, int)} 保证不复用。</p>
     *
     * @param entity 表单定义实体（版本号需已确定）
     */
    private void writeVersionSnapshot(FormDefinitionPo entity) {
        FormDefinitionVersionPo snapshot = new FormDefinitionVersionPo();
        snapshot.setDefinitionId(entity.getId());
        snapshot.setVersion(entity.getVersion());
        snapshot.setSnapshotSeq(nextSnapshotSeq(entity.getId()));
        snapshot.setSnapshotJson(buildSnapshot(entity));
        formDefinitionVersionMapper.insert(snapshot);
    }

    /**
     * 计算下一个快照序号（同定义内单调递增）
     *
     * @param definitionId 表单定义ID
     * @return 下一个快照序号
     */
    private Long nextSnapshotSeq(Long definitionId) {
        LambdaQueryWrapper<FormDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.select(FormDefinitionVersionPo::getSnapshotSeq);
        wrapper.orderByDesc(FormDefinitionVersionPo::getSnapshotSeq);
        wrapper.last("LIMIT 1");
        List<FormDefinitionVersionPo> latest = formDefinitionVersionMapper.selectList(wrapper);
        Long max = latest.isEmpty() || latest.get(0).getSnapshotSeq() == null
                ? 0L : latest.get(0).getSnapshotSeq();
        return max + 1;
    }

    /**
     * 计算回滚后的新版本号：取「当前版本」与「历史快照最大版本」的较大者加一，保证版本号永不复用
     *
     * @param definitionId 表单定义ID
     * @param currentVersion 当前版本号
     * @return 新版本号
     */
    private int nextDefinitionVersion(Long definitionId, int currentVersion) {
        LambdaQueryWrapper<FormDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.select(FormDefinitionVersionPo::getVersion);
        wrapper.orderByDesc(FormDefinitionVersionPo::getVersion);
        wrapper.last("LIMIT 1");
        List<FormDefinitionVersionPo> latest = formDefinitionVersionMapper.selectList(wrapper);
        int maxSnapshot = latest.isEmpty() || latest.get(0).getVersion() == null
                ? 0 : latest.get(0).getVersion();
        return Math.max(currentVersion, maxSnapshot) + 1;
    }

    /**
     * 构建「定义 + 字段 + 选项」完整快照 JSON
     *
     * @param entity 表单定义实体
     * @return 快照 JSON 文本
     */
    private String buildSnapshot(FormDefinitionPo entity) {
        ObjectNode root = SNAPSHOT_MAPPER.createObjectNode();
        ObjectNode definition = root.putObject("definition");
        definition.put("code", entity.getCode());
        definition.put("name", entity.getName());
        definition.put("tableDefinitionId", entity.getTableDefinitionId());
        definition.put("schemaJson", entity.getSchemaJson());
        definition.put("version", entity.getVersion());
        definition.put("status", entity.getStatus());
        definition.put("remark", entity.getRemark());
        List<FormFieldConfigPo> configs = listConfigs(entity.getId());
        Map<Long, List<FormFieldOptionPo>> optionMap = loadOptions(configs);
        ArrayNode fields = root.putArray("fields");
        for (FormFieldConfigPo config : configs) {
            fields.add(toSnapshotField(config, optionMap.get(config.getId())));
        }
        return root.toString();
    }

    /**
     * 字段配置 → 快照字段节点（含选项）
     *
     * @param config 字段配置
     * @param options 选项列表（可空）
     * @return 快照字段节点
     */
    private static ObjectNode toSnapshotField(FormFieldConfigPo config, List<FormFieldOptionPo> options) {
        ObjectNode node = SNAPSHOT_MAPPER.createObjectNode();
        node.put("columnMetaId", config.getColumnMetaId());
        node.put("fieldLabel", config.getFieldLabel());
        node.put("widgetType", config.getWidgetType());
        node.put("showInList", config.getShowInList());
        node.put("showInForm", config.getShowInForm());
        node.put("editable", config.getEditable());
        node.put("sortNo", config.getSortNo());
        node.put("colSpan", config.getColSpan());
        node.put("placeholder", config.getPlaceholder());
        node.put("defaultValue", config.getDefaultValue());
        node.put("validateJson", config.getValidateJson());
        node.put("remark", config.getRemark());
        node.put("status", config.getStatus());
        ArrayNode optionArray = node.putArray("options");
        if (options == null) {
            return node;
        }
        for (FormFieldOptionPo option : options) {
            ObjectNode optionNode = optionArray.addObject();
            optionNode.put("optionLabel", option.getOptionLabel());
            optionNode.put("optionValue", option.getOptionValue());
            optionNode.put("sortNo", option.getSortNo());
        }
        return node;
    }

    /**
     * 读取文本属性
     *
     * @param node 父节点
     * @param field 属性名
     * @return 文本值；缺失或非文本时为 {@code null}
     */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
    }

    /**
     * 读取整型属性
     *
     * @param node 父节点
     * @param field 属性名
     * @return 整型值；缺失或非数值时为 {@code null}
     */
    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    /**
     * 读取长整型属性
     *
     * @param node 父节点
     * @param field 属性名
     * @return 长整型值；缺失或非数值时为 {@code null}
     */
    private static Long longOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    /**
     * 版本快照实体 → 视图对象
     *
     * @param snapshot 版本快照实体
     * @return 版本快照视图对象
     */
    private static FormDefinitionVersionVO toVersionVo(FormDefinitionVersionPo snapshot) {
        FormDefinitionVersionVO vo = new FormDefinitionVersionVO();
        vo.setVersion(snapshot.getVersion());
        vo.setSnapshotSeq(snapshot.getSnapshotSeq());
        vo.setCreateTime(snapshot.getCreateTime());
        return vo;
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
