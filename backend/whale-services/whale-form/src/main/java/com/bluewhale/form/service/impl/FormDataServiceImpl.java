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
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.service.FormDataService;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormField;
import com.bluewhale.form.support.FormSchemaParser;
import com.bluewhale.form.vo.FormDataVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    /** 表单数据最大长度（字符数）：64KB */
    private static final int MAX_DATA_LENGTH = 65536;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** JSON 解析器（仅读写，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FormDataMapper formDataMapper;

    private final FormDefinitionMapper formDefinitionMapper;

    private final FormTableRegistryMapper formTableRegistryMapper;

    private final FormDataEngine formDataEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormDataVO submit(FormDataSubmitRequest request, String submitter, Long tenantId) {
        FormDefinition definition = requireDefinition(request.getDefinitionId());
        validateDataSize(request.getDataJson());
        FormTableRegistry registry = requireRegistry(definition.getCode(), definition.getVersion());
        List<FormField> fields = FormSchemaParser.parse(registry.getSchemaJson());
        Map<String, Object> data = parseData(request.getDataJson());
        validateFields(fields, data);
        FormData entity = new FormData();
        entity.setTenantId(tenantId);
        entity.setDefinitionId(definition.getId());
        entity.setDefinitionCode(definition.getCode());
        entity.setDefinitionVersion(registry.getDefinitionVersion());
        entity.setBizKey(request.getBizKey());
        entity.setDataJson(request.getDataJson());
        entity.setSubmitter(submitter);
        entity.setStatus(STATUS_NORMAL);
        formDataMapper.insert(entity);
        formDataEngine.insert(registry.getPhysicalTable(), entity, fields, data);
        return toVo(entity, data);
    }

    @Override
    public FormDataVO getDetail(Long id, Long tenantId) {
        FormData entity = requireData(id, tenantId);
        FormTableRegistry registry = requireRegistry(entity.getDefinitionCode(), entity.getDefinitionVersion());
        List<FormField> fields = FormSchemaParser.parse(registry.getSchemaJson());
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
        FormTableRegistry registry = requireRegistry(entity.getDefinitionCode(), entity.getDefinitionVersion());
        formDataEngine.logicDelete(registry.getPhysicalTable(), id, tenantId);
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
     * 查询指定（编码 + 版本）的物理表登记，未发布则抛出 {@code PARAM_ERROR}
     *
     * @param code 表单定义编码
     * @param version 表单定义版本
     * @return 物理表登记实体
     */
    private FormTableRegistry requireRegistry(String code, Integer version) {
        LambdaQueryWrapper<FormTableRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormTableRegistry::getDefinitionCode, code);
        wrapper.eq(FormTableRegistry::getDefinitionVersion, version);
        FormTableRegistry registry = formTableRegistryMapper.selectOne(wrapper);
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
}
