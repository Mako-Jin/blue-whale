package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.converter.FormConverter;
import com.bluewhale.form.model.dto.FormDataFilter;
import com.bluewhale.form.model.request.FormDataQueryRequest;
import com.bluewhale.form.model.request.FormDataSubmitRequest;
import com.bluewhale.form.model.request.FormDataUpdateRequest;
import com.bluewhale.form.entity.FormColumnMetaEntity;
import com.bluewhale.form.entity.FormDataEntity;
import com.bluewhale.form.entity.FormDefinitionEntity;
import com.bluewhale.form.entity.FormTableDefinitionEntity;
import com.bluewhale.form.entity.FormTableRegistryEntity;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.service.FormDataService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataExportSupport;
import com.bluewhale.form.support.FormDataType;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormMetadataCompiler;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.model.vo.FormArchiveVO;
import com.bluewhale.form.model.vo.FormDataExportVO;
import com.bluewhale.form.model.vo.FormDataVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    /** 导出单次最大条数 */
    private static final long EXPORT_MAX_ROWS = 5000L;

    /** 导出格式：CSV */
    private static final String FORMAT_CSV = "csv";

    /** 导出格式：XLSX */
    private static final String FORMAT_XLSX = "xlsx";

    /** 动态字段条件最大个数 */
    private static final int MAX_FILTERS = 10;

    /** 动态字段条件 JSON 文本最大长度 */
    private static final int MAX_FILTER_JSON_LENGTH = 4096;

    /** 动态字段检索单次最多回捞的记录数（多版本物理表合并时按此上限截断） */
    private static final int MAX_FILTER_FETCH = 5000;

    /** 过滤字段名字符白名单（与物理表列名规则一致） */
    private static final Pattern FILTER_FIELD_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** 日期值格式（yyyy-MM-dd） */
    private static final Pattern DATE_VALUE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** 日期时间值格式（yyyy-MM-dd HH:mm[:ss] 或 yyyy-MM-ddTHH:mm[:ss]） */
    private static final Pattern DATETIME_VALUE_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?$");

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

    private final FormColumnMetaMapper formColumnMetaMapper;

    private final FormDataEngine formDataEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDataVO submit(FormDataSubmitRequest request, String submitter, Long tenantId) {
        FormDefinitionEntity definition = requireDefinition(request.getDefinitionId());
        String clientRequestId = normalizeClientRequestId(request.getClientRequestId());
        FormDataEntity existing = findByClientRequestId(tenantId, definition.getCode(), clientRequestId);
        if (existing != null) {
            return toVo(existing, parseDataQuietly(existing.getDataJson()));
        }
        validateDataSize(request.getDataJson());
        Integer formVersion = definition.getVersion() == null ? DEFAULT_VERSION : definition.getVersion();
        FormTableDefinitionEntity table = definition.getTableDefinitionId() == null
                ? null : requireTableDefinition(definition.getTableDefinitionId());
        String routeCode = table == null ? definition.getCode() : table.getTableCode();
        Integer routeVersion = table == null ? formVersion : tableVersionOf(table);
        FormTableRegistryEntity registry = requireRegistry(routeCode, routeVersion);
        String schema = table == null ? registry.getSchemaJson() : definition.getSchemaJson();
        List<FormField> fields = FormSchemaParser.parse(schema);
        Map<String, Object> data = parseData(request.getDataJson());
        validateFields(fields, data);
        FormDataEntity entity = new FormDataEntity();
        entity.setTenantId(tenantId);
        entity.setDefinitionId(definition.getId());
        entity.setDefinitionCode(definition.getCode());
        entity.setDefinitionVersion(formVersion);
        entity.setBizKey(request.getBizKey());
        entity.setClientRequestId(clientRequestId);
        entity.setDataJson(request.getDataJson());
        entity.setSubmitter(submitter);
        entity.setStatus(STATUS_NORMAL);
        if (table != null) {
            entity.setTableDefinitionId(table.getId());
            entity.setTableCode(table.getTableCode());
            entity.setTableVersion(routeVersion);
            entity.setFormDefinitionId(definition.getId());
        }
        try {
            formDataMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一键兜底后回查首次提交结果；软删记录占用键时无匹配则给出明确提示
            FormDataEntity duplicated = findByClientRequestId(tenantId, definition.getCode(), clientRequestId);
            if (duplicated == null) {
                throw paramError("客户端请求ID已被占用，请重新生成 clientRequestId");
            }
            return toVo(duplicated, parseDataQuietly(duplicated.getDataJson()));
        }
        formDataEngine.insert(registry.getPhysicalTable(), entity, fields, data);
        return toVo(entity, data);
    }

    /**
     * 归一化客户端幂等键：空白视为未提供
     *
     * @param clientRequestId 原始幂等键
     * @return 去空白后的幂等键；未提供返回 {@code null}
     */
    private static String normalizeClientRequestId(String clientRequestId) {
        return StringUtils.hasText(clientRequestId) ? clientRequestId.trim() : null;
    }

    /**
     * 按（租户, 表单编码, 幂等键）查询已提交记录
     *
     * @param tenantId 租户ID
     * @param definitionCode 表单编码
     * @param clientRequestId 幂等键（{@code null} 表示未启用幂等）
     * @return 已存在的表单数据；未命中返回 {@code null}
     */
    private FormDataEntity findByClientRequestId(Long tenantId, String definitionCode, String clientRequestId) {
        if (clientRequestId == null) {
            return null;
        }
        LambdaQueryWrapper<FormDataEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDataEntity::getTenantId, tenantId);
        wrapper.eq(FormDataEntity::getDefinitionCode, definitionCode);
        wrapper.eq(FormDataEntity::getClientRequestId, clientRequestId);
        return formDataMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDataVO update(Long id, FormDataUpdateRequest request, Long tenantId) {
        FormDataEntity entity = requireData(id, tenantId);
        FormDefinitionEntity definition = requireDefinition(entity.getDefinitionId());
        validateDataSize(request.getDataJson());
        FormTableRegistryEntity registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        List<FormField> tableFields = FormSchemaParser.parseStorage(schemaOf(registry));
        List<FormField> viewFields = StringUtils.hasText(definition.getSchemaJson())
                ? FormSchemaParser.parse(definition.getSchemaJson()) : tableFields;
        Map<String, Object> merged = new LinkedHashMap<>(parseDataQuietly(entity.getDataJson()));
        merged.putAll(parseData(request.getDataJson()));
        assertKnownFields(tableFields, merged);
        validateValues(viewFields, merged);
        entity.setDataJson(writeData(merged));
        formDataMapper.updateById(entity);
        formDataEngine.update(registry.getPhysicalTable(), entity, writableFields(viewFields, tableFields), merged);
        return toVo(entity, merged);
    }

    @Override
    public FormDataVO getDetail(Long id, Long tenantId) {
        FormDataEntity entity = requireData(id, tenantId);
        FormTableRegistryEntity registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        List<FormField> fields = FormSchemaParser.parseStorage(schemaOf(registry));
        Map<String, Object> data =
                formDataEngine.selectFields(registry.getPhysicalTable(), entity.getId(), tenantId, fields);
        return toVo(entity, data);
    }

    @Override
    public FormArchiveVO getArchiveDetail(Long id, Long tenantId) {
        FormDataEntity entity = requireData(id, tenantId);
        FormTableRegistryEntity registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        List<FormField> fields = FormSchemaParser.parseStorage(schemaOf(registry));
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
        Page<FormDataEntity> query = request.toPage();
        long current = query.getCurrent();
        long size = query.getSize();
        if (!filters.isEmpty()) {
            if (!StringUtils.hasText(request.getDefinitionCode())) {
                throw paramError("动态字段条件检索需先指定表单编码");
            }
            FormDefinitionEntity definition = requireDefinitionByCode(request.getDefinitionCode());
            FilteredRows matched = selectByFilters(definition, request, filters, tenantId,
                    (current - 1) * size, size);
            List<FormDataVO> filtered = matched.rows().stream().map(entity -> toVo(entity, null)).toList();
            return PageResult.of(filtered, matched.total(), current, size);
        }
        LambdaQueryWrapper<FormDataEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDataEntity::getTenantId, tenantId);
        wrapper.eq(StringUtils.hasText(request.getDefinitionCode()), FormDataEntity::getDefinitionCode,
                request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getBizKey()), FormDataEntity::getBizKey, request.getBizKey());
        wrapper.eq(StringUtils.hasText(request.getSubmitter()), FormDataEntity::getSubmitter, request.getSubmitter());
        wrapper.orderByDesc(FormDataEntity::getCreateTime);
        Page<FormDataEntity> page = formDataMapper.selectPage(query, wrapper);
        List<FormDataVO> records = page.getRecords().stream().map(entity -> toVo(entity, null)).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long tenantId) {
        FormDataEntity entity = requireData(id, tenantId);
        formDataMapper.deleteById(id);
        FormTableRegistryEntity registry = requireRegistry(routeCodeOf(entity), routeVersionOf(entity));
        formDataEngine.logicDelete(registry.getPhysicalTable(), id, tenantId);
    }

    @Override
    public FormDataExportVO export(FormDataQueryRequest request, Long tenantId, String format) {
        String exportFormat = normalizeExportFormat(format);
        if (!StringUtils.hasText(request.getDefinitionCode())) {
            throw paramError("请先指定表单编码后再导出");
        }
        FormDefinitionEntity definition = requireDefinitionByCode(request.getDefinitionCode());
        List<FormField> fields = FormSchemaParser.parse(definition.getSchemaJson());
        List<FormDataFilter> filters = parseFilters(request.getFilters());
        boolean truncated;
        List<FormDataEntity> exportData;
        if (filters.isEmpty()) {
            exportData = listExportData(request, tenantId, EXPORT_MAX_ROWS + 1);
            truncated = exportData.size() > EXPORT_MAX_ROWS;
            if (truncated) {
                exportData = new ArrayList<>(exportData.subList(0, (int) EXPORT_MAX_ROWS));
            }
        } else {
            FilteredRows matched = selectByFilters(definition, request, filters, tenantId, 0L, EXPORT_MAX_ROWS + 1);
            exportData = matched.rows();
            truncated = matched.total() > exportData.size();
        }
        List<String> keys = new ArrayList<>();
        List<String> headers = new ArrayList<>(BASE_EXPORT_HEADERS);
        for (FormField field : fields) {
            keys.add(field.getKey());
            headers.add(StringUtils.hasText(field.getLabel()) ? field.getLabel() : field.getKey());
        }
        appendLegacyColumns(keys, headers, exportData);
        List<List<String>> rows = new ArrayList<>();
        for (FormDataEntity entity : exportData) {
            rows.add(exportRow(entity, keys));
        }
        String filename = "form-data-" + definition.getCode() + "-"
                + LocalDateTime.now().format(FILE_TIME_FORMATTER) + "." + exportFormat;
        byte[] content = FORMAT_XLSX.equals(exportFormat)
                ? FormDataExportSupport.xlsx(definition.getName(), headers, rows)
                : FormDataExportSupport.csv(headers, rows);
        return new FormDataExportVO(content, filename, FormDataExportSupport.contentType(exportFormat), truncated);
    }

    /**
     * 校验并归一化导出格式（仅接受 csv / xlsx）
     *
     * @param format 原始格式参数
     * @return 小写格式标识
     * @throws BizException 格式缺失或不受支持时抛出 {@code PARAM_ERROR}
     */
    private static String normalizeExportFormat(String format) {
        String value = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT_CSV.equals(value) && !FORMAT_XLSX.equals(value)) {
            throw paramError("导出格式仅支持 csv 或 xlsx");
        }
        return value;
    }

    /**
     * 追加历史版本独有字段列：导出按表单编码跨版本取数，当前 schema 之外的字段以字段编码作为表头，
     * 避免历史版本数据静默丢失
     *
     * @param keys 动态字段编码列表（就地追加）
     * @param headers 表头列表（就地追加）
     * @param exportData 待导出数据
     */
    private static void appendLegacyColumns(List<String> keys, List<String> headers,
                                            List<FormDataEntity> exportData) {
        Set<String> known = new HashSet<>(keys);
        for (FormDataEntity entity : exportData) {
            for (String key : parseDataQuietly(entity.getDataJson()).keySet()) {
                if (known.add(key)) {
                    keys.add(key);
                    headers.add(key);
                }
            }
        }
    }

    /**
     * 组装单条导出数据行：公共列 + 动态列值（取自 {@code dataJson}，缺失输出空串）
     *
     * @param entity 表单数据实体
     * @param keys 动态字段编码列表
     * @return 单元格文本列表
     */
    private static List<String> exportRow(FormDataEntity entity, List<String> keys) {
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
        for (String key : keys) {
            row.add(formatCell(data.get(key)));
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
    private List<FormDataEntity> listExportData(FormDataQueryRequest request, Long tenantId, long limit) {
        LambdaQueryWrapper<FormDataEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDataEntity::getTenantId, tenantId);
        wrapper.eq(FormDataEntity::getDefinitionCode, request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getBizKey()), FormDataEntity::getBizKey, request.getBizKey());
        wrapper.eq(StringUtils.hasText(request.getSubmitter()), FormDataEntity::getSubmitter, request.getSubmitter());
        wrapper.orderByDesc(FormDataEntity::getCreateTime);
        Page<FormDataEntity> page = formDataMapper.selectPage(new Page<>(1L, limit), wrapper);
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
    private FilteredRows selectByFilters(FormDefinitionEntity definition, FormDataQueryRequest request,
                                         List<FormDataFilter> filters, Long tenantId, long offset, long limit) {
        assertFilterFieldsDefined(definition, filters);
        List<FormTableRegistryEntity> tables = listFilterTables(definition);
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
        for (FormTableRegistryEntity registry : tables) {
            List<FormField> fields = fieldsOf(registry);
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
        List<FormDataEntity> ordered = loadOrdered(ids);
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
    private List<FormDataEntity> loadOrdered(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FormDataEntity> byId = new LinkedHashMap<>();
        for (FormDataEntity entity : formDataMapper.selectBatchIds(ids)) {
            byId.put(entity.getId(), entity);
        }
        List<FormDataEntity> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FormDataEntity entity = byId.get(id);
            if (entity != null) {
                ordered.add(entity);
            }
        }
        ordered.sort(Comparator.comparing(FormDataEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FormDataEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return ordered;
    }

    /**
     * 查询该表单定义关联的生效物理表（元数据模型按表定义、旧模型按表单编码）
     *
     * @param definition 表单定义
     * @return 物理表登记列表
     */
    private List<FormTableRegistryEntity> listFilterTables(FormDefinitionEntity definition) {
        LambdaQueryWrapper<FormTableRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        if (definition.getTableDefinitionId() == null) {
            wrapper.eq(FormTableRegistryEntity::getDefinitionCode, definition.getCode());
        } else {
            wrapper.eq(FormTableRegistryEntity::getTableDefinitionId, definition.getTableDefinitionId());
        }
        wrapper.eq(FormTableRegistryEntity::getStatus, STATUS_NORMAL);
        return formTableRegistryMapper.selectList(wrapper);
    }

    /**
     * 取物理表对应版本的字段定义：优先使用发布时写入登记的 schema 快照
     *
     * @param registry 物理表登记
     * @return 字段列表；无法确定该版本结构时返回 {@code null}（该版本不参与过滤）
     */
    private List<FormField> fieldsOf(FormTableRegistryEntity registry) {
        String schema = schemaOf(registry);
        return StringUtils.hasText(schema) ? FormSchemaParser.parseStorage(schema) : null;
    }

    /**
     * 取物理表版本的 schema：优先发布时写入的快照；存量登记无快照时，按该表版本的列元数据
     * 即时重编译兜底，并只保留物理表实际存在的列（避免读到未发布的元数据列）。
     *
     * <p><b>重编译时机</b>：仅在读取存量（无快照）登记时兜底；正常路径由表定义发布时
     * 写入快照，视图编辑只重编译表单自身 schema，不触碰物理表快照。</p>
     *
     * @param registry 物理表登记
     * @return schema_json 文本；无法确定时返回 {@code null}
     */
    private String schemaOf(FormTableRegistryEntity registry) {
        if (StringUtils.hasText(registry.getSchemaJson())) {
            return registry.getSchemaJson();
        }
        if (registry.getTableDefinitionId() == null) {
            return null;
        }
        Map<String, String> physical = formDataEngine.describeColumns(registry.getPhysicalTable());
        LambdaQueryWrapper<FormColumnMetaEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormColumnMetaEntity::getTableDefinitionId, registry.getTableDefinitionId());
        wrapper.eq(FormColumnMetaEntity::getTableVersion, registry.getTableVersion());
        wrapper.eq(FormColumnMetaEntity::getStatus, STATUS_NORMAL);
        wrapper.orderByAsc(FormColumnMetaEntity::getSortNo);
        List<FormColumnMetaEntity> columns = formColumnMetaMapper.selectList(wrapper).stream()
                .filter(column -> physical.containsKey(column.getColumnCode()))
                .toList();
        return columns.isEmpty() ? null : FormMetadataCompiler.compileTable(columns);
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
    private static void assertFilterFieldsDefined(FormDefinitionEntity definition, List<FormDataFilter> filters) {
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
    private record FilteredRows(List<FormDataEntity> rows, long total) {
    }

    /**
     * 按编码查询表单定义（多版本时取版本号最大者）
     *
     * @param code 表单定义编码
     * @return 表单定义实体
     * @throws BizException 编码无匹配定义时抛出 {@code PARAM_ERROR}
     */
    private FormDefinitionEntity requireDefinitionByCode(String code) {
        LambdaQueryWrapper<FormDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinitionEntity::getCode, code);
        List<FormDefinitionEntity> definitions = formDefinitionMapper.selectList(wrapper);
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
    private FormDefinitionEntity requireDefinition(Long definitionId) {
        FormDefinitionEntity definition = formDefinitionMapper.selectById(definitionId);
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
    private FormTableDefinitionEntity requireTableDefinition(Long tableDefinitionId) {
        FormTableDefinitionEntity table = formTableDefinitionMapper.selectById(tableDefinitionId);
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
    private static String routeCodeOf(FormDataEntity entity) {
        return entity.getTableCode() == null ? entity.getDefinitionCode() : entity.getTableCode();
    }

    /**
     * 取表单数据的物理表路由版本：元数据模型用 {@code tableVersion}，旧模型回落 {@code definitionVersion}
     *
     * @param entity 表单数据实体
     * @return 路由版本
     */
    private static Integer routeVersionOf(FormDataEntity entity) {
        return entity.getTableCode() == null ? entity.getDefinitionVersion() : entity.getTableVersion();
    }

    /**
     * 取表定义版本（空则回落默认版本）
     *
     * @param table 表定义实体
     * @return 表定义版本
     */
    private static int tableVersionOf(FormTableDefinitionEntity table) {
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
    private FormTableRegistryEntity requireRegistry(String code, Integer version) {
        LambdaQueryWrapper<FormTableRegistryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistryEntity::getTableCode, code);
        wrapper.eq(FormTableRegistryEntity::getTableVersion, version);
        wrapper.eq(FormTableRegistryEntity::getStatus, STATUS_NORMAL);
        FormTableRegistryEntity registry = formTableRegistryMapper.selectOne(wrapper);
        if (registry == null) {
            LambdaQueryWrapper<FormTableRegistryEntity> legacy = new LambdaQueryWrapper<>();
            legacy.eq(FormTableRegistryEntity::getDefinitionCode, code);
            legacy.eq(FormTableRegistryEntity::getDefinitionVersion, version);
            legacy.eq(FormTableRegistryEntity::getStatus, STATUS_NORMAL);
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
    private FormDataEntity requireData(Long id, Long tenantId) {
        FormDataEntity entity = formDataMapper.selectById(id);
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
        assertKnownFields(fields, data);
        validateValues(fields, data);
    }

    /**
     * 校验未知字段：数据中出现、但不在结构定义内的 key 抛参数错误
     *
     * @param fields 结构定义字段列表（字段白名单）
     * @param data 字段值映射
     * @throws BizException 出现未知字段时抛出 {@code PARAM_ERROR}
     */
    private static void assertKnownFields(List<FormField> fields, Map<String, Object> data) {
        Set<String> allowed = new HashSet<>();
        for (FormField field : fields) {
            allowed.add(field.getKey());
        }
        for (String key : data.keySet()) {
            if (!allowed.contains(key)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "字段 " + key + " 不在表单结构定义中");
            }
        }
    }

    /**
     * 校验字段必填与取值（不校验未知字段）
     *
     * @param fields 结构定义字段列表
     * @param data 字段值映射
     * @throws BizException 必填缺失或取值不合规时抛出 {@code PARAM_ERROR}
     */
    private static void validateValues(List<FormField> fields, Map<String, Object> data) {
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
     * 取可写入物理表的字段：视图字段与物理表列的交集（视图新增字段尚未落到该表版本时自动跳过）
     *
     * @param viewFields 视图结构字段
     * @param tableFields 物理表结构字段
     * @return 可写入的字段列表
     */
    private static List<FormField> writableFields(List<FormField> viewFields, List<FormField> tableFields) {
        Set<String> physicalKeys = new HashSet<>();
        for (FormField field : tableFields) {
            physicalKeys.add(field.getKey());
        }
        return viewFields.stream().filter(field -> physicalKeys.contains(field.getKey())).toList();
    }

    /**
     * 校验字段值：复合字段按形态校验，基础字段校验类型与日期格式
     *
     * @param field 字段定义
     * @param value 字段值
     * @throws BizException 值形态或类型不合规时抛出 {@code PARAM_ERROR}
     */
    private static void validateFieldValue(FormField field, Object value) {
        if (FormDataType.ATTACHMENT.equals(field.getType())) {
            validateAttachment(field, value);
            return;
        }
        if (FormDataType.SUBTABLE.equals(field.getType())) {
            validateSubtable(field, value);
            return;
        }
        if (!matchesType(field.getType(), value)) {
            throw paramError("字段 " + field.getLabel() + " 的值类型不符");
        }
        if (FormDataType.DATE.equals(field.getType()) || FormDataType.DATETIME.equals(field.getType())) {
            validateTemporalValue(field, String.valueOf(value));
        }
    }

    /**
     * 校验日期/日期时间值的格式与真实日历值
     *
     * @param field 字段定义
     * @param text 字段值文本
     * @throws BizException 格式非法或日期不存在时抛出 {@code PARAM_ERROR}
     */
    private static void validateTemporalValue(FormField field, String text) {
        try {
            if (FormDataType.DATE.equals(field.getType())) {
                if (!DATE_VALUE_PATTERN.matcher(text).matches()) {
                    throw paramError("字段 " + field.getLabel() + " 的日期格式应为 yyyy-MM-dd");
                }
                LocalDate.parse(text);
                return;
            }
            if (!DATETIME_VALUE_PATTERN.matcher(text).matches()) {
                throw paramError("字段 " + field.getLabel() + " 的日期时间格式应为 yyyy-MM-dd HH:mm[:ss]");
            }
            String normalized = text.replace(' ', 'T');
            if (normalized.length() == 16) {
                normalized = normalized + ":00";
            }
            LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw paramError("字段 " + field.getLabel() + " 的日期时间值不合法");
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
    private static FormDataVO toVo(FormDataEntity entity, Map<String, Object> fields) {
        FormDataVO vo = FormConverter.INSTANCE.toDataVo(entity);
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
