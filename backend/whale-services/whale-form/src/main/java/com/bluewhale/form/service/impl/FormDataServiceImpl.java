package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataFilter;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.dto.FormDataUpdateRequest;
import com.bluewhale.form.entity.FormData;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.service.FormDataService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataExportSupport;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.vo.FormArchiveVO;
import com.bluewhale.form.vo.FormDataExportVO;
import com.bluewhale.form.vo.FormDataVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表单数据服务实现（主从分离：主表记录 + 版本化物理表结构化字段）。
 *
 * <p>提交时按 registry 的 schema 快照校验字段并双写主表与物理表；详情按记录的
 * {@code definition_version} 路由到对应物理表读取结构化字段；租户行级过滤（spec §三.3.6、§三.3.9）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FormDataServiceImpl implements FormDataService {

    /** 数据状态：有效 */
    private static final int STATUS_NORMAL = 1;

    /** 版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 表单数据最大长度（字符数）：64KB */
    private static final int MAX_DATA_LENGTH = 65536;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 导出单次最大条数 */
    private static final long EXPORT_MAX_ROWS = 5000L;

    /** 动态字段条件最大个数 */
    private static final int MAX_FILTERS = 10;

    /** 动态字段条件 JSON 文本最大长度 */
    private static final int MAX_FILTER_JSON_LENGTH = 4096;

    /** 动态字段检索单次最多回捞的记录数（多版本物理表合并时按此上限截断） */
    private static final int MAX_FILTER_FETCH = 5000;

    /** 过滤字段名字符白名单（与物理表列名规则一致） */
    private static final Pattern FILTER_FIELD_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** 导出文件名时间戳格式 */
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 导出公共列（位于动态字段之前） */
    private static final List<String> BASE_EXPORT_HEADERS = List.of(
            "ID", "表单编码", "版本", "业务键", "提交人", "提交时间", "状态");

    /** JSON 解析器（仅读写，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FormDataMapper formDataMapper;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormTableDefinitionMapper formTableDefinitionMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormDataEngine formDataEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDataVO submit(FormDataSubmitRequest request, String submitter, Long tenantId) {
        FormDefinition definition = requireDefinition(request.getDefinitionId());
        validateDataSize(request.getDataJson());
        Integer formVersion = definition.getVersion() == null ? DEFAULT_VERSION : definition.getVersion();
        FormTableDefinition table = definition.getTableDefinitionId() == null
                ? null : requireTableDefinition(definition.getTableDefinitionId());
        String routeCode = table == null ? definition.getCode() : table.getTableCode();
        Integer routeVersion = table == null ? formVersion : tableVersionOf(table);
        FormTableRegistry registry = requireRegistry(routeCode, routeVersion);
        String schema = table == null ? registry.getSchemaJson() : definition.getSchemaJson();
        List<FormField> fields = FormSchemaParser.parse(schema);
        Map<String, Object> data = parseData(request.getDataJson());
        validateFields(fields, data);
        FormData entity = new FormData();
        entity.setTenantId(tenantId);
        entity.setDefinitionId(definition.getId());
        entity.setDefinitionCode(definition.getCode());
        entity.setDefinitionVersion(formVersion);
        entity.setBizKey(request.getBizKey());
        entity.setDataJson(request.getDataJson());
        entity.setSubmitter(submitter);
        entity.setStatus(STATUS_NORMAL);
        if (table != null) {
            entity.setTableDefinitionId(table.getId());
            entity.setTableCode(table.getTableCode());
            entity.setTableVersion(routeVersion);
            entity.setFormDefinitionId(definition.getId());
        }
        formDataMapper.insert(entity);
        formDataEngine.insert(registry.getPhysicalTable(), entity, fields, data);
        return toVo(entity, data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDataVO update(Long id, FormDataUpdateRequest request, Long tenantId) {
        FormData entity = requireData(id, tenantId);
        requireDefinition(entity.getDefinitionId());
        validateDataSize(request.getDataJson());
        FormTableRegistry registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        String schema = entity.getTableCode() == null
                ? registry.getSchemaJson() : requireDefinition(entity.getDefinitionId()).getSchemaJson();
        List<FormField> fields = FormSchemaParser.parse(schema);
        Map<String, Object> merged = new LinkedHashMap<>(parseDataQuietly(entity.getDataJson()));
        merged.putAll(parseData(request.getDataJson()));
        validateFields(fields, merged);
        entity.setDataJson(writeData(merged));
        formDataMapper.updateById(entity);
        formDataEngine.update(registry.getPhysicalTable(), entity, fields, merged);
        return toVo(entity, merged);
    }

    @Override
    public FormDataVO getDetail(Long id, Long tenantId) {
        FormData entity = requireData(id, tenantId);
        FormTableRegistry registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        String schema = entity.getTableCode() == null
                ? registry.getSchemaJson() : requireDefinition(entity.getDefinitionId()).getSchemaJson();
        List<FormField> fields = FormSchemaParser.parse(schema);
        Map<String, Object> data =
                formDataEngine.selectFields(registry.getPhysicalTable(), entity.getId(), tenantId, fields);
        return toVo(entity, data);
    }

    @Override
    public FormArchiveVO getArchiveDetail(Long id, Long tenantId) {
        FormData entity = requireData(id, tenantId);
        FormTableRegistry registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        String schema = entity.getTableCode() == null
                ? registry.getSchemaJson() : requireDefinition(entity.getDefinitionId()).getSchemaJson();
        List<FormField> fields = FormSchemaParser.parse(schema);
        Map<String, Object> data =
                formDataEngine.selectFields(registry.getPhysicalTable(), entity.getId(), tenantId, fields);
        FormArchiveVO vo = new FormArchiveVO();
        vo.setData(toVo(entity, data));
        vo.setFieldDefinitions(fields);
        return vo;
    }

    @Override
    public PageResult<FormDataVO> page(FormDataQueryRequest request, Long tenantId) {
        List<FormDataFilter> filters = parseFilters(request.getFilters());
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent(), 1L);
        if (!filters.isEmpty()) {
            if (!StringUtils.hasText(request.getDefinitionCode())) {
                throw paramError("动态字段条件检索需先指定表单编码");
            }
            FormDefinition definition = requireDefinitionByCode(request.getDefinitionCode());
            FilteredRows matched = selectByFilters(definition, request, filters, tenantId,
                    (current - 1) * size, size);
            List<FormDataVO> filtered = matched.rows().stream().map(entity -> toVo(entity, null)).toList();
            return PageResult.of(filtered, matched.total(), current, size);
        }
        LambdaQueryWrapper<FormData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormData::getTenantId, tenantId);
        wrapper.eq(StringUtils.hasText(request.getDefinitionCode()), FormData::getDefinitionCode,
                request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getBizKey()), FormData::getBizKey, request.getBizKey());
        wrapper.eq(StringUtils.hasText(request.getSubmitter()), FormData::getSubmitter, request.getSubmitter());
        wrapper.orderByDesc(FormData::getCreateTime);
        Page<FormData> page = formDataMapper.selectPage(new Page<>(current, size), wrapper);
        List<FormDataVO> records = page.getRecords().stream().map(entity -> toVo(entity, null)).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long tenantId) {
        FormData entity = requireData(id, tenantId);
        formDataMapper.deleteById(id);
        FormTableRegistry registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        formDataEngine.logicDelete(registry.getPhysicalTable(), id, tenantId);
    }

    @Override
    public FormDataExportVO export(FormDataQueryRequest request, Long tenantId, String format) {
        if (!StringUtils.hasText(request.getDefinitionCode())) {
            throw paramError("请先指定表单编码后再导出");
        }
        FormDefinition definition = requireDefinitionByCode(request.getDefinitionCode());
        List<FormField> fields = FormSchemaParser.parse(definition.getSchemaJson());
        List<String> headers = exportHeaders(fields);
        List<FormDataFilter> filters = parseFilters(request.getFilters());
        List<FormData> exportData = filters.isEmpty()
                ? listExportData(request, tenantId)
                : selectByFilters(definition, request, filters, tenantId, 0L, EXPORT_MAX_ROWS).rows();
        List<List<String>> rows = new ArrayList<>();
        for (FormData entity : exportData) {
            rows.add(exportRow(entity, fields));
        }
        String extension = FormDataExportSupport.extension(format);
        String filename = "form-data-" + definition.getCode() + "-"
                + LocalDateTime.now().format(FILE_TIME_FORMATTER) + "." + extension;
        byte[] content = "xlsx".equalsIgnoreCase(format)
                ? FormDataExportSupport.xlsx(definition.getName(), headers, rows)
                : FormDataExportSupport.csv(headers, rows);
        return new FormDataExportVO(content, filename, FormDataExportSupport.contentType(format));
    }

    /**
     * 组装导出表头：公共列 + 表单视图数据字段（按 {@code schema_json} 中的字段顺序）
     *
     * @param fields 数据字段列表
     * @return 表头文本列表
     */
    private static List<String> exportHeaders(List<FormField> fields) {
        List<String> headers = new ArrayList<>(BASE_EXPORT_HEADERS);
        for (FormField field : fields) {
            headers.add(StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getKey());
        }
        return headers;
    }

    /**
     * 组装单条导出数据行：公共列 + 动态列值（取自 {@code dataJson}，缺失输出空串）
     *
     * @param entity 表单数据实体
     * @param fields 数据字段列表
     * @return 单元格文本列表
     */
    private static List<String> exportRow(FormData entity, List<FormField> fields) {
        List<String> row = new ArrayList<>();
        row.add(String.valueOf(entity.getId()));
        row.add(entity.getDefinitionCode());
        row.add(String.valueOf(entity.getDefinitionVersion() == null
                ? DEFAULT_VERSION : entity.getDefinitionVersion()));
        row.add(entity.getBizKey() == null ? "" : entity.getBizKey());
        row.add(entity.getSubmitter() == null ? "" : entity.getSubmitter());
        row.add(entity.getCreateTime() == null ? "" : entity.getCreateTime().toString());
        row.add(entity.getStatus() == null ? "" : String.valueOf(entity.getStatus()));
        Map<String, Object> data = parseDataQuietly(entity.getDataJson());
        for (FormField field : fields) {
            row.add(formatCell(data.get(field.getKey())));
        }
        return row;
    }

    /**
     * 解析 {@code dataJson}（容错：非法 JSON 视为空映射，不阻断导出）
     *
     * @param dataJson 表单数据 JSON
     * @return 字段值映射
     */
    private static Map<String, Object> parseDataQuietly(String dataJson) {
        try {
            return MAPPER.readValue(dataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 单元格值转文本（{@code null} 输出空串）
     *
     * @param value 原始值
     * @return 单元格文本
     */
    private static String formatCell(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 查询待导出表单数据（按租户与查询条件过滤，按创建时间倒序，上限 {@code EXPORT_MAX_ROWS} 条）
     *
     * @param request 查询条件（{@code definitionCode} 已由调用方保证非空）
     * @param tenantId 租户ID
     * @return 表单数据实体列表
     */
    private List<FormData> listExportData(FormDataQueryRequest request, Long tenantId) {
        LambdaQueryWrapper<FormData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormData::getTenantId, tenantId);
        wrapper.eq(FormData::getDefinitionCode, request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getBizKey()), FormData::getBizKey, request.getBizKey());
        wrapper.eq(StringUtils.hasText(request.getSubmitter()), FormData::getSubmitter, request.getSubmitter());
        wrapper.orderByDesc(FormData::getCreateTime);
        Page<FormData> page = formDataMapper.selectPage(new Page<>(1L, EXPORT_MAX_ROWS), wrapper);
        return page.getRecords();
    }

    /**
     * 按动态字段条件检索（跨该表单已发布的各版本物理表合并，按创建时间倒序回捞当页）
     *
     * <p>各版本物理表独立查询：仅当该版本的 schema 快照包含全部过滤字段时才参与；命中总数累加，
     * 当页 ID 按各表 {@code offset + limit} 截断后合并排序取窗口，再回主表按 ID 装载实体。</p>
     *
     * @param definition 表单定义（多版本取最大版本，用于定位表定义）
     * @param request 查询条件（业务键/提交人同步下推到物理表）
     * @param filters 动态字段过滤条件
     * @param tenantId 租户ID
     * @param offset 偏移量
     * @param limit 条数
     * @return 当页实体与命中总数
     */
    private FilteredRows selectByFilters(FormDefinition definition, FormDataQueryRequest request,
                                         List<FormDataFilter> filters, Long tenantId, long offset, long limit) {
        assertFilterFieldsDefined(definition, filters);
        List<FormTableRegistry> tables = listFilterTables(definition);
        if (tables.isEmpty()) {
            throw paramError("表单未发布，暂不支持动态字段检索");
        }
        if (offset >= MAX_FILTER_FETCH) {
            throw paramError("动态字段检索仅支持前 " + MAX_FILTER_FETCH + " 条记录分页");
        }
        int fetchLimit = (int) Math.min(offset + limit, MAX_FILTER_FETCH);
        Set<Long> ids = new LinkedHashSet<>();
        long total = 0L;
        boolean usable = false;
        for (FormTableRegistry registry : tables) {
            List<FormField> fields = fieldsOf(registry, definition);
            if (fields == null || !containsAllFields(fields, filters)) {
                continue;
            }
            usable = true;
            total += formDataEngine.countByFilters(registry.getPhysicalTable(), tenantId, fields, filters,
                    request.getBizKey(), request.getSubmitter());
            ids.addAll(formDataEngine.selectIdsByFilters(registry.getPhysicalTable(), tenantId, fields, filters,
                    request.getBizKey(), request.getSubmitter(), fetchLimit));
        }
        if (!usable) {
            throw paramError("过滤字段在当前已发布版本中不可用");
        }
        List<FormData> ordered = loadOrdered(ids);
        int from = (int) Math.min(offset, ordered.size());
        int to = (int) Math.min(offset + limit, ordered.size());
        return new FilteredRows(new ArrayList<>(ordered.subList(from, to)), total);
    }

    /**
     * 按 ID 集合装载主表实体并按创建时间倒序排序（物理表已按租户过滤，此处仅补齐主表字段）
     *
     * @param ids 物理表命中的记录ID（按各表创建时间倒序）
     * @return 排序后的实体列表
     */
    private List<FormData> loadOrdered(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FormData> byId = new LinkedHashMap<>();
        for (FormData entity : formDataMapper.selectBatchIds(ids)) {
            byId.put(entity.getId(), entity);
        }
        List<FormData> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FormData entity = byId.get(id);
            if (entity != null) {
                ordered.add(entity);
            }
        }
        ordered.sort(Comparator.comparing(FormData::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FormData::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return ordered;
    }

    /**
     * 查询该表单定义关联的生效物理表（元数据模型按表定义、旧模型按表单编码）
     *
     * @param definition 表单定义
     * @return 物理表登记列表
     */
    private List<FormTableRegistry> listFilterTables(FormDefinition definition) {
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        if (definition.getTableDefinitionId() == null) {
            wrapper.eq(FormTableRegistry::getDefinitionCode, definition.getCode());
        } else {
            wrapper.eq(FormTableRegistry::getTableDefinitionId, definition.getTableDefinitionId());
        }
        wrapper.eq(FormTableRegistry::getStatus, STATUS_NORMAL);
        return formTableRegistryMapper.selectList(wrapper);
    }

    /**
     * 取物理表对应版本的字段定义：优先使用登记内的 schema 快照；元数据模型回落当前表单定义 schema
     *
     * @param registry 物理表登记
     * @param definition 表单定义
     * @return 字段列表；无法确定该版本结构时返回 {@code null}（该版本不参与过滤）
     */
    private static List<FormField> fieldsOf(FormTableRegistry registry, FormDefinition definition) {
        if (StringUtils.hasText(registry.getSchemaJson())) {
            return FormSchemaParser.parse(registry.getSchemaJson());
        }
        if (registry.getTableDefinitionId() != null
                && registry.getTableDefinitionId().equals(definition.getTableDefinitionId())) {
            return FormSchemaParser.parse(definition.getSchemaJson());
        }
        return null;
    }

    /**
     * 判断字段定义是否覆盖全部过滤字段（缺字段的版本无法满足条件，直接跳过）
     *
     * @param fields 该版本字段定义
     * @param filters 过滤条件
     * @return 全部覆盖返回 {@code true}
     */
    private static boolean containsAllFields(List<FormField> fields, List<FormDataFilter> filters) {
        Set<String> keys = new HashSet<>();
        for (FormField field : fields) {
            keys.add(field.getKey());
        }
        for (FormDataFilter filter : filters) {
            if (!keys.contains(filter.getField())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验过滤字段存在于当前表单结构定义且类型支持条件检索
     *
     * @param definition 表单定义
     * @param filters 过滤条件
     * @throws BizException 字段不存在或类型不支持时抛出 {@code PARAM_ERROR}
     */
    private static void assertFilterFieldsDefined(FormDefinition definition, List<FormDataFilter> filters) {
        Map<String, FormField> fieldMap = new LinkedHashMap<>();
        for (FormField field : FormSchemaParser.parse(definition.getSchemaJson())) {
            fieldMap.put(field.getKey(), field);
        }
        for (FormDataFilter filter : filters) {
            FormField field = fieldMap.get(filter.getField());
            if (field == null) {
                throw paramError("过滤字段不在当前表单结构定义中：" + filter.getField());
            }
            if (!FormDataType.isFilterable(field.getType())) {
                String label = StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getKey();
                throw paramError("字段 " + label + " 不支持条件检索");
            }
        }
    }

    /**
     * 解析 {@code filters} JSON 数组文本（空文本视为无条件）
     *
     * @param raw JSON 数组文本
     * @return 过滤条件列表
     * @throws BizException JSON 非法、条件过多或字段名非法时抛出 {@code PARAM_ERROR}
     */
    private static List<FormDataFilter> parseFilters(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        if (raw.length() > MAX_FILTER_JSON_LENGTH) {
            throw paramError("过滤条件 JSON 不能超过 " + MAX_FILTER_JSON_LENGTH + " 字符");
        }
        List<FormDataFilter> filters;
        try {
            filters = MAPPER.readValue(raw, new TypeReference<List<FormDataFilter>>() {
            });
        } catch (Exception e) {
            throw paramError("filters 不是合法的过滤条件 JSON 数组");
        }
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        if (filters.size() > MAX_FILTERS) {
            throw paramError("过滤条件最多 " + MAX_FILTERS + " 个");
        }
        for (FormDataFilter filter : filters) {
            if (filter == null || !StringUtils.hasText(filter.getField())
                    || !FILTER_FIELD_PATTERN.matcher(filter.getField()).matches()) {
                throw paramError("过滤条件的 field 非法");
            }
        }
        return filters;
    }

    /**
     * 动态字段检索结果：当页实体 + 命中总数
     *
     * @param rows 当页实体
     * @param total 命中总数
     */
    private record FilteredRows(List<FormData> rows, long total) {
    }

    /**
     * 按编码查询表单定义（多版本时取版本号最大者）
     *
     * @param code 表单定义编码
     * @return 表单定义实体
     * @throws BizException 编码无匹配定义时抛出 {@code PARAM_ERROR}
     */
    private FormDefinition requireDefinitionByCode(String code) {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinition::getCode, code);
        List<FormDefinition> definitions = formDefinitionMapper.selectList(wrapper);
        if (definitions.isEmpty()) {
            throw paramError("表单编码不存在：" + code);
        }
        return definitions.stream()
                .max(Comparator.comparingInt(item -> item.getVersion() == null ? DEFAULT_VERSION
                        : item.getVersion()))
                .orElse(definitions.get(0));
    }

    /**
     * 查询表单定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param definitionId 表单定义ID
     * @return 表单定义实体
     */
    private FormDefinition requireDefinition(Long definitionId) {
        FormDefinition definition = formDefinitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return definition;
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
     * 取表单数据的物理表路由编码：元数据模型用 {@code tableCode}，旧模型回落 {@code definitionCode}
     *
     * @param entity 表单数据实体
     * @return 路由编码
     */
    private static String routeCodeOf(FormData entity) {
        return entity.getTableCode() == null ? entity.getDefinitionCode() : entity.getTableCode();
    }

    /**
     * 取表单数据的物理表路由版本：元数据模型用 {@code tableVersion}，旧模型回落 {@code definitionVersion}
     *
     * @param entity 表单数据实体
     * @return 路由版本
     */
    private static Integer routeVersionOf(FormData entity) {
        return entity.getTableCode() == null ? entity.getDefinitionVersion() : entity.getTableVersion();
    }

    /**
     * 取表定义版本（空则回落默认版本）
     *
     * @param table 表定义实体
     * @return 表定义版本
     */
    private static int tableVersionOf(FormTableDefinition table) {
        return table.getVersion() == null ? DEFAULT_VERSION : table.getVersion();
    }

    /**
     * 查询指定（路由编码 + 版本）的物理表登记：先按表维度（元数据模型）查找，
     * 未命中再按表单维度（旧模型）回落；均未命中则抛出 {@code PARAM_ERROR}
     *
     * @param code 路由编码（表编码或表单编码）
     * @param version 路由版本
     * @return 物理表登记实体
     * @throws BizException 未发布时抛出 {@code PARAM_ERROR}
     */
    private FormTableRegistry requireRegistry(String code, Integer version) {
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getTableCode, code);
        wrapper.eq(FormTableRegistry::getTableVersion, version);
        FormTableRegistry registry = formTableRegistryMapper.selectOne(wrapper);
        if (registry == null) {
            LambdaQueryWrapper<FormTableRegistry> legacy = new LambdaQueryWrapper<>();
            legacy.eq(FormTableRegistry::getDefinitionCode, code);
            legacy.eq(FormTableRegistry::getDefinitionVersion, version);
            registry = formTableRegistryMapper.selectOne(legacy);
        }
        if (registry == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单未发布或版本已变更，请先发布");
        }
        return registry;
    }

    /**
     * 查询表单数据并要求租户一致，不存在或跨租户则抛出 {@code NOT_FOUND}
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     * @return 表单数据实体
     */
    private FormData requireData(Long id, Long tenantId) {
        FormData entity = formDataMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 解析表单数据 JSON 为字段值映射
     *
     * @param dataJson 表单数据 JSON
     * @return 字段值映射
     * @throws BizException 非法 JSON 或非 JSON 对象时抛出 {@code PARAM_ERROR}
     */
    private static Map<String, Object> parseData(String dataJson) {
        try {
            return MAPPER.readValue(dataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单数据不是合法的 JSON 对象");
        }
    }

    /**
     * 将字段值映射序列化为 JSON 文本
     *
     * @param data 字段值映射
     * @return JSON 文本
     * @throws BizException 序列化失败时抛出 {@code PARAM_ERROR}
     */
    private static String writeData(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单数据序列化失败");
        }
    }

    /**
     * 按 schema 白名单校验字段：未知字段与必填缺失均抛参数错误
     *
     * @param fields 动态字段列表
     * @param data 字段值映射
     * @throws BizException 校验不通过时抛出 {@code PARAM_ERROR}
     */
    private static void validateFields(List<FormField> fields, Map<String, Object> data) {
        Set<String> allowed = new HashSet<>();
        for (FormField field : fields) {
            allowed.add(field.getKey());
        }
        for (String key : data.keySet()) {
            if (!allowed.contains(key)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "字段 " + key + " 不在表单结构定义中");
            }
        }
        for (FormField field : fields) {
            Object value = data.get(field.getKey());
            if (field.isRequired() && isBlank(value)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "字段 " + field.getLabel() + " 为必填");
            }
            if (value == null) {
                continue;
            }
            validateFieldValue(field, value);
        }
    }

    /**
     * 校验附件/子表等复合字段的值形态；基础字段保持既有宽松校验
     *
     * @param field 字段定义
     * @param value 字段值
     * @throws BizException 值形态不合规时抛出 {@code PARAM_ERROR}
     */
    private static void validateFieldValue(FormField field, Object value) {
        if (FormDataType.ATTACHMENT.equals(field.getType())) {
            validateAttachment(field, value);
            return;
        }
        if (FormDataType.SUBTABLE.equals(field.getType())) {
            validateSubtable(field, value);
        }
    }

    /**
     * 校验附件值：必须是文件 ID 数字数组，且满足 multiple / maxCount
     *
     * @param field 字段定义
     * @param value 字段值
     * @throws BizException 非数组、元素非数字或数量越界时抛出 {@code PARAM_ERROR}
     */
    private static void validateAttachment(FormField field, Object value) {
        if (!(value instanceof Collection<?> files)) {
            throw paramError("字段 " + field.getLabel() + " 的值必须是文件 ID 数组");
        }
        if (Boolean.FALSE.equals(field.getMultiple()) && files.size() > 1) {
            throw paramError("字段 " + field.getLabel() + " 仅允许上传一个文件");
        }
        if (field.getMaxCount() != null && field.getMaxCount() > 0 && files.size() > field.getMaxCount()) {
            throw paramError("字段 " + field.getLabel() + " 最多允许上传 " + field.getMaxCount() + " 个文件");
        }
        for (Object item : files) {
            if (!isIntegerNumber(item)) {
                throw paramError("字段 " + field.getLabel() + " 的文件 ID 必须为数字");
            }
        }
    }

    /**
     * 校验子表值：必须是行对象数组；空行忽略，按 children 校验每行必填与类型
     *
     * @param field 字段定义
     * @param value 字段值
     * @throws BizException 非数组、行非对象、未知子字段、必填缺失、类型不符或行数越界时抛出 {@code PARAM_ERROR}
     */
    private static void validateSubtable(FormField field, Object value) {
        if (!(value instanceof Collection<?> rows)) {
            throw paramError("字段 " + field.getLabel() + " 的值必须是行对象数组");
        }
        List<Map<?, ?>> effectiveRows = effectiveRows(rows, field.getLabel());
        assertRowCount(field, effectiveRows.size());
        List<FormField> children = field.getChildren() == null ? List.of() : field.getChildren();
        Set<String> childKeys = new HashSet<>();
        for (FormField child : children) {
            childKeys.add(child.getKey());
        }
        for (Map<?, ?> row : effectiveRows) {
            validateSubtableRow(field, children, childKeys, row);
        }
    }

    /**
     * 提取有效子表行（非空行），并校验每行必须为对象
     *
     * @param rows 原始行集合
     * @param label 字段显示名（错误提示）
     * @return 有效行列表
     * @throws BizException 行不是对象时抛出 {@code PARAM_ERROR}
     */
    private static List<Map<?, ?>> effectiveRows(Collection<?> rows, String label) {
        List<Map<?, ?>> effectiveRows = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                throw paramError("字段 " + label + " 的子表行必须为对象");
            }
            if (!isEmptyRow(map)) {
                effectiveRows.add(map);
            }
        }
        return effectiveRows;
    }

    /**
     * 校验子表有效行数是否满足 minRows / maxRows
     *
     * @param field 字段定义
     * @param rowCount 有效行数
     * @throws BizException 行数越界时抛出 {@code PARAM_ERROR}
     */
    private static void assertRowCount(FormField field, int rowCount) {
        if (field.getMaxRows() != null && field.getMaxRows() > 0 && rowCount > field.getMaxRows()) {
            throw paramError("字段 " + field.getLabel() + " 的子表行数不能超过 " + field.getMaxRows());
        }
        if (field.getMinRows() != null && field.getMinRows() > 0 && rowCount < field.getMinRows()) {
            throw paramError("字段 " + field.getLabel() + " 的子表至少需要 " + field.getMinRows() + " 行");
        }
    }

    /**
     * 校验单个子表行：禁止未知子字段，并校验必填与类型
     *
     * @param field 子表字段定义
     * @param children 子字段定义列表
     * @param childKeys 合法子字段 key 集合
     * @param row 行对象
     * @throws BizException 校验不通过时抛出 {@code PARAM_ERROR}
     */
    private static void validateSubtableRow(FormField field, List<FormField> children,
                                            Set<String> childKeys, Map<?, ?> row) {
        for (Object rowKey : row.keySet()) {
            if (!childKeys.contains(String.valueOf(rowKey))) {
                throw paramError("字段 " + field.getLabel() + " 存在未定义的子字段：" + rowKey);
            }
        }
        for (FormField child : children) {
            Object cell = row.get(child.getKey());
            if (child.isRequired() && isBlank(cell)) {
                throw paramError("字段 " + field.getLabel() + " 的子字段 " + child.getLabel() + " 为必填");
            }
            if (cell != null && !matchesType(child.getType(), cell)) {
                throw paramError("字段 " + field.getLabel() + " 的子字段 " + child.getLabel() + " 类型不符");
            }
        }
    }

    /**
     * 判断子表行是否为空行（所有单元格均视为空）
     *
     * @param row 行对象
     * @return 空行返回 {@code true}
     */
    private static boolean isEmptyRow(Map<?, ?> row) {
        for (Object value : row.values()) {
            if (!isBlank(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 子字段值类型匹配（仅基础类型）
     *
     * @param type 子字段类型
     * @param value 单元格值
     * @return 匹配返回 {@code true}
     */
    private static boolean matchesType(String type, Object value) {
        switch (type) {
            case FormDataType.NUMBER:
                return value instanceof Number;
            case FormDataType.BOOL:
                return value instanceof Boolean
                        || (value instanceof Number number && (number.longValue() == 0L || number.longValue() == 1L));
            case FormDataType.TEXT:
            case FormDataType.TEXTAREA:
            case FormDataType.SELECT:
            case FormDataType.DATE:
            case FormDataType.DATETIME:
                return value instanceof String;
            default:
                return false;
        }
    }

    /**
     * 判断是否为整数数字（排除字符串、浮点与 {@code null}）
     *
     * @param value 待判定值
     * @return 整数数字返回 {@code true}
     */
    private static boolean isIntegerNumber(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger;
    }

    /**
     * 判断字段值是否为空（{@code null}、空白字符串、空集合或空映射）
     *
     * @param value 字段值
     * @return 为空返回 {@code true}
     */
    private static boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return !StringUtils.hasText(text);
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return value instanceof Map<?, ?> map && map.isEmpty();
    }

    /**
     * 校验表单数据长度不超过 64KB
     *
     * @param dataJson 表单数据JSON
     * @throws BizException 超长时抛出 {@code PARAM_ERROR}
     */
    private static void validateDataSize(String dataJson) {
        if (dataJson != null && dataJson.length() > MAX_DATA_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "表单数据长度不能超过 65536 字符");
        }
    }

    /**
     * 表单数据实体转视图对象（{@code fields} 为可选结构化字段）
     *
     * @param entity 表单数据实体
     * @param fields 结构化字段值（列表查询传 {@code null}）
     * @return 表单数据视图对象
     */
    private static FormDataVO toVo(FormData entity, Map<String, Object> fields) {
        FormDataVO vo = new FormDataVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setFields(fields);
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
