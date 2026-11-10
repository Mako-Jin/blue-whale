package com.bluewhale.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.assistant.dto.DefinitionSaveRequest;
import com.bluewhale.assistant.dto.DefinitionVO;
import com.bluewhale.assistant.entity.AsstDefinition;
import com.bluewhale.assistant.entity.AsstToolGrant;
import com.bluewhale.assistant.mapper.AsstDefinitionMapper;
import com.bluewhale.assistant.mapper.AsstToolGrantMapper;
import com.bluewhale.assistant.support.ToolRegistryClient;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 助手定义服务：创建/更新/发布/停用/删除、工具白名单与已发布校验。
 *
 * <p>状态机：{@code draft → published ⇄ disabled}；仅 {@code published} 助手可建会话与问答。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsstDefinitionService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 状态：草稿 */
    public static final String STATUS_DRAFT = "draft";

    /** 状态：已发布 */
    public static final String STATUS_PUBLISHED = "published";

    /** 状态：已停用 */
    public static final String STATUS_DISABLED = "disabled";

    /** 模型参数允许的键（与 whale-ai ModelParams 契约对齐，键名按 whale-ai 侧 snake_case） */
    private static final Set<String> MODEL_PARAM_KEYS = Set.of("model", "temperature", "max_tokens");

    private final AsstDefinitionMapper definitionMapper;

    private final AsstToolGrantMapper toolGrantMapper;

    private final ToolRegistryClient toolRegistryClient;

    /**
     * 创建助手（草稿态）。
     *
     * @param request 保存请求
     * @param userId 当前用户
     * @return 视图对象
     */
    public DefinitionVO create(DefinitionSaveRequest request, Long userId) {
        if (!StringUtils.hasText(request.getCode())) {
            throw new BizException("助手编码不能为空");
        }
        Long existed = definitionMapper.selectCount(
                new LambdaQueryWrapper<AsstDefinition>().eq(AsstDefinition::getCode, request.getCode()));
        if (existed != null && existed > 0) {
            throw new BizException("助手编码已存在：" + request.getCode());
        }
        AsstDefinition definition = new AsstDefinition();
        definition.setCode(request.getCode());
        definition.setStatus(STATUS_DRAFT);
        definition.setCreatedBy(userId);
        applyRequest(definition, request);
        definitionMapper.insert(definition);
        return toVo(definition);
    }

    /**
     * 更新助手（编码不可改；状态不变）。
     *
     * @param id      助手ID
     * @param request 保存请求
     * @return 视图对象
     */
    public DefinitionVO update(Long id, DefinitionSaveRequest request) {
        AsstDefinition definition = requireExists(id);
        applyRequest(definition, request);
        definitionMapper.updateById(definition);
        return toVo(definition);
    }

    /**
     * 发布助手（draft/disabled → published）。
     *
     * @param id 助手ID
     * @return 视图对象
     */
    public DefinitionVO publish(Long id) {
        AsstDefinition definition = requireExists(id);
        definition.setStatus(STATUS_PUBLISHED);
        definitionMapper.updateById(definition);
        return toVo(definition);
    }

    /**
     * 停用助手（published → disabled；问答入口随即拒绝）。
     *
     * @param id 助手ID
     * @return 视图对象
     */
    public DefinitionVO disable(Long id) {
        AsstDefinition definition = requireExists(id);
        definition.setStatus(STATUS_DISABLED);
        definitionMapper.updateById(definition);
        return toVo(definition);
    }

    /**
     * 删除助手（逻辑删除；历史会话保留但不再有入口）。
     *
     * @param id 助手ID
     */
    public void delete(Long id) {
        requireExists(id);
        definitionMapper.deleteById(id);
    }

    /**
     * 助手定义分页（管理视角）。
     *
     * @param keyword 名称/编码模糊过滤（可空）
     * @param status  状态过滤（可空）
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<DefinitionVO> page(String keyword, String status, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<AsstDefinition> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AsstDefinition::getName, keyword)
                    .or().like(AsstDefinition::getCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AsstDefinition::getStatus, status);
        }
        wrapper.orderByDesc(AsstDefinition::getUpdateTime).orderByDesc(AsstDefinition::getId);
        Page<AsstDefinition> page = definitionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<DefinitionVO> records = new ArrayList<>(page.getRecords().size());
        for (AsstDefinition definition : page.getRecords()) {
            records.add(toVo(definition));
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 助手详情（含工具白名单）。
     *
     * @param id 助手ID
     * @return 视图对象
     */
    public DefinitionVO get(Long id) {
        return toVo(requireExists(id));
    }

    /**
     * 整表覆盖式设置工具白名单。
     *
     * @param id        助手ID
     * @param toolCodes 已授权工具编码全集
     * @param userId    操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void setTools(Long id, List<String> toolCodes, Long userId) {
        requireExists(id);
        List<String> normalized = normalizeToolCodes(toolCodes);
        validateAgainstRegistry(normalized);
        Map<String, AsstToolGrant> existing = grantMap(id);
        for (Map.Entry<String, AsstToolGrant> entry : existing.entrySet()) {
            AsstToolGrant grant = entry.getValue();
            boolean granted = normalized.contains(entry.getKey());
            if (grant.getGranted() != null && grant.getGranted() == (granted ? 1 : 0)) {
                continue;
            }
            grant.setGranted(granted ? 1 : 0);
            if (granted) {
                grant.setApprovedBy(userId);
            }
            toolGrantMapper.updateById(grant);
        }
        for (String code : normalized) {
            if (existing.containsKey(code)) {
                continue;
            }
            AsstToolGrant grant = new AsstToolGrant();
            grant.setAssistantId(id);
            grant.setToolCode(code);
            grant.setGranted(1);
            grant.setApprovedBy(userId);
            toolGrantMapper.insert(grant);
        }
    }

    /**
     * 已授权工具编码集合。
     *
     * @param id 助手ID
     * @return 工具编码列表
     */
    public List<String> toolWhitelist(Long id) {
        return new ArrayList<>(grantMap(id).keySet());
    }

    /**
     * 已发布助手清单（问答视角，供平台工作台与业务内嵌入口选择助手）。
     *
     * @return 已发布助手视图列表（按更新时间倒序，不含管理字段）
     */
    public List<DefinitionVO> listPublished() {
        LambdaQueryWrapper<AsstDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstDefinition::getStatus, STATUS_PUBLISHED);
        wrapper.orderByDesc(AsstDefinition::getUpdateTime).orderByDesc(AsstDefinition::getId);
        List<DefinitionVO> result = new ArrayList<>();
        for (AsstDefinition definition : definitionMapper.selectList(wrapper)) {
            result.add(toVo(definition));
        }
        return result;
    }

    /**
     * 取已发布助手（问答入口校验）。
     *
     * @param id 助手ID
     * @return 已发布助手定义
     */
    public AsstDefinition requirePublished(Long id) {
        AsstDefinition definition = requireExists(id);
        if (!STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw new BizException("助手未发布或已停用，无法发起问答");
        }
        return definition;
    }

    /**
     * 按编码取已发布助手（开放 API 入口校验）。
     *
     * @param code 助手编码
     * @return 已发布助手定义
     */
    public AsstDefinition requirePublishedByCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("助手编码不能为空");
        }
        AsstDefinition definition = definitionMapper.selectOne(
                new LambdaQueryWrapper<AsstDefinition>().eq(AsstDefinition::getCode, code).last("LIMIT 1"));
        if (definition == null) {
            throw new BizException("助手不存在：" + code);
        }
        if (!STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw new BizException("助手未发布或已停用，无法发起问答");
        }
        return definition;
    }

    /**
     * 校验工具编码均已在 whale-ai 注册表登记（白名单只能收敛、不能放大，fail-closed）。
     *
     * @param toolCodes 待授权工具编码
     */
    private void validateAgainstRegistry(List<String> toolCodes) {
        if (toolCodes.isEmpty()) {
            return;
        }
        Set<String> registered;
        try {
            registered = toolRegistryClient.listToolNames();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("工具注册表查询被中断，请稍后再试");
        } catch (IOException e) {
            log.warn("工具注册表查询失败：{}", e.getMessage());
            throw new BizException("工具注册表暂不可用（whale-ai 未就绪），请稍后再试");
        }
        List<String> unknown = toolCodes.stream().filter(code -> !registered.contains(code)).toList();
        if (!unknown.isEmpty()) {
            throw new BizException("工具未在 whale-ai 注册表登记：" + String.join("、", unknown));
        }
    }

    /**
     * 按 ID 取助手（存在性校验）。
     *
     * @param id 助手ID
     * @return 助手定义
     */
    public AsstDefinition requireExists(Long id) {
        AsstDefinition definition = definitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException("助手不存在");
        }
        return definition;
    }

    /**
     * 应用保存请求到实体（编码除外）。
     *
     * @param definition 实体
     * @param request    保存请求
     */
    private void applyRequest(AsstDefinition definition, DefinitionSaveRequest request) {
        definition.setName(request.getName());
        definition.setAvatar(request.getAvatar());
        definition.setDescription(request.getDescription());
        definition.setSystemPrompt(request.getSystemPrompt());
        definition.setModelParamsJson(toJson(normalizeModelParams(request.getModelParams())));
        definition.setKnowledgeScope(request.getKnowledgeScope());
    }

    /**
     * 归一化模型参数：仅保留白名单键（model/temperature/max_tokens），非法键直接拒绝。
     *
     * @param params 原始参数
     * @return 归一化参数（无有效键返回 {@code null}）
     */
    static Map<String, Object> normalizeModelParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!MODEL_PARAM_KEYS.contains(key)) {
                throw new BizException("模型参数不支持键：" + key + "（仅允许 model/temperature/max_tokens）");
            }
            if (entry.getValue() != null) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 归一化工具编码：去空白、去重、保序。
     *
     * @param toolCodes 原始编码列表
     * @return 归一化列表
     */
    private static List<String> normalizeToolCodes(List<String> toolCodes) {
        List<String> normalized = new ArrayList<>();
        if (toolCodes == null) {
            return normalized;
        }
        for (String code : toolCodes) {
            if (code == null) {
                continue;
            }
            String value = code.trim();
            if (!value.isEmpty() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    /**
     * 助手已授权工具映射（granted=1）。
     *
     * @param assistantId 助手ID
     * @return 工具编码 → 授权记录
     */
    private Map<String, AsstToolGrant> grantMap(Long assistantId) {
        LambdaQueryWrapper<AsstToolGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstToolGrant::getAssistantId, assistantId);
        wrapper.eq(AsstToolGrant::getGranted, 1);
        Map<String, AsstToolGrant> result = new LinkedHashMap<>();
        for (AsstToolGrant grant : toolGrantMapper.selectList(wrapper)) {
            result.put(grant.getToolCode(), grant);
        }
        return result;
    }

    /**
     * 实体转视图对象。
     *
     * @param definition 实体
     * @return 视图对象
     */
    private DefinitionVO toVo(AsstDefinition definition) {
        return DefinitionVO.builder()
                .id(String.valueOf(definition.getId()))
                .code(definition.getCode())
                .name(definition.getName())
                .avatar(definition.getAvatar())
                .description(definition.getDescription())
                .systemPrompt(definition.getSystemPrompt())
                .modelParams(parseJsonMap(definition.getModelParamsJson()))
                .knowledgeScope(definition.getKnowledgeScope())
                .status(definition.getStatus())
                .toolWhitelist(toolWhitelist(definition.getId()))
                .createdBy(definition.getCreatedBy() == null ? null : String.valueOf(definition.getCreatedBy()))
                .createTime(format(definition.getCreateTime()))
                .updateTime(format(definition.getUpdateTime()))
                .build();
    }

    /**
     * 解析模型参数 JSON。
     *
     * @param json JSON 串
     * @return 参数 Map；空或解析失败返回 {@code null}
     */
    private static Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("模型参数 JSON 解析失败", e);
            return null;
        }
    }

    /**
     * 序列化为 JSON。
     *
     * @param value 值
     * @return JSON 串；空值返回 {@code null}
     */
    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("模型参数序列化失败", e);
        }
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 字符串；空值返回 {@code null}
     */
    private static String format(java.time.LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
