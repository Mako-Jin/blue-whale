package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
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
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.vo.FormDataExportVO;
import com.bluewhale.form.vo.FormDataVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public PageResult<FormDataVO> page(FormDataQueryRequest request, Long tenantId) {
        LambdaQueryWrapper<FormData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormData::getTenantId, tenantId);
        wrapper.eq(StringUtils.hasText(request.getDefinitionCode()), FormData::getDefinitionCode,
                request.getDefinitionCode());
        wrapper.eq(StringUtils.hasText(request.getBizKey()), FormData::getBizKey, request.getBizKey());
        wrapper.eq(StringUtils.hasText(request.getSubmitter()), FormData::getSubmitter, request.getSubmitter());
        wrapper.orderByDesc(FormData::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<FormData> page = formDataMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
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
        List<List<String>> rows = new ArrayList<>();
        for (FormData entity : listExportData(request, tenantId)) {
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
            if (field.isRequired() && isBlank(data.get(field.getKey()))) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "字段 " + field.getLabel() + " 为必填");
            }
        }
    }

    /**
     * 判断字段值是否为空（{@code null} 或空白字符串）
     *
     * @param value 字段值
     * @return 为空返回 {@code true}
     */
    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String text && !StringUtils.hasText(text));
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
