package com.bluewhale.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.assistant.dto.CitationVO;
import com.bluewhale.assistant.dto.MessageVO;
import com.bluewhale.assistant.dto.SessionCreateRequest;
import com.bluewhale.assistant.dto.SessionVO;
import com.bluewhale.assistant.entity.AsstDefinition;
import com.bluewhale.assistant.entity.AsstMessage;
import com.bluewhale.assistant.entity.AsstSession;
import com.bluewhale.assistant.mapper.AsstMessageMapper;
import com.bluewhale.assistant.mapper.AsstSessionMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.assistant.config.AssistantProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话服务：创建（仅已发布助手）、归属校验、消息落库、历史读取、分页与删除。
 *
 * <p>所有读写均以网关 {@code X-User-Id} 为隔离条件，禁止跨用户访问。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsstSessionService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 会话标题最大长度 */
    private static final int TITLE_LIMIT = 50;

    /** 来源：平台工作台 */
    public static final String SOURCE_PLATFORM = "platform";

    /** 来源：业务内嵌 */
    public static final String SOURCE_EMBED = "embed";

    /** 来源：开放接口 */
    public static final String SOURCE_OPENAPI = "openapi";

    /** 允许的来源集合 */
    private static final java.util.Set<String> SOURCES =
            java.util.Set.of(SOURCE_PLATFORM, SOURCE_EMBED, SOURCE_OPENAPI);

    /** 角色：用户 */
    public static final String ROLE_USER = "user";

    /** 角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";

    private final AsstSessionMapper sessionMapper;

    private final AsstMessageMapper messageMapper;

    private final AsstDefinitionService definitionService;

    private final AssistantProperties properties;

    /**
     * 创建会话（助手必须已发布）。
     *
     * @param request 创建请求
     * @param userId  当前用户
     * @return 会话视图
     */
    public SessionVO create(SessionCreateRequest request, Long userId) {
        AsstDefinition definition = definitionService.requirePublished(request.getAssistantId());
        AsstSession session = insertSession(definition, request, userId);
        return toVo(session, definition.getName());
    }

    /**
     * 创建会话实体（助手必须已发布；供视图层与开放 API 复用）。
     *
     * @param request 创建请求
     * @param userId  当前用户
     * @return 会话实体
     */
    public AsstSession createSession(SessionCreateRequest request, Long userId) {
        AsstDefinition definition = definitionService.requirePublished(request.getAssistantId());
        return insertSession(definition, request, userId);
    }

    /**
     * 落库新会话（来源白名单校验）。
     *
     * @param definition 已发布助手定义
     * @param request    创建请求
     * @param userId     当前用户
     * @return 会话实体
     */
    private AsstSession insertSession(AsstDefinition definition, SessionCreateRequest request, Long userId) {
        String source = StringUtils.hasText(request.getSource()) ? request.getSource() : SOURCE_PLATFORM;
        if (!SOURCES.contains(source)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.session.source-invalid",
                    "来源非法：" + source + "（仅允许 platform/embed/openapi）", source);
        }
        AsstSession session = new AsstSession();
        session.setAssistantId(definition.getId());
        session.setUserId(userId);
        session.setTitle("新会话");
        session.setSource(source);
        session.setChannelRef(request.getChannelRef());
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 校验会话归属并返回。
     *
     * @param sessionId 会话ID
     * @param userId    当前用户
     * @return 会话
     */
    public AsstSession requireOwned(Long sessionId, Long userId) {
        AsstSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.session.not-found", "会话不存在或无权访问");
        }
        return session;
    }

    /**
     * 追加消息（含单会话消息数与单条正文字数上限）。
     *
     * @param sessionId     会话ID
     * @param role          角色
     * @param content       正文
     * @param citationsJson 引用 JSON（用户消息为空）
     * @param tokens        token 统计
     * @param elapsedMs     耗时（毫秒，用户消息为 0）
     * @param degraded      是否降级（用户消息为 0）
     * @return 落库消息
     */
    public AsstMessage append(Long sessionId, String role, String content, String citationsJson,
                              int tokens, int elapsedMs, int degraded) {
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.getMessageMaxBytes()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.session.content-too-large",
                    "消息正文超出上限（" + properties.getMessageMaxBytes() + " 字节）",
                    properties.getMessageMaxBytes());
        }
        if (ROLE_USER.equals(role)) {
            long count = messageMapper.selectCount(
                    new LambdaQueryWrapper<AsstMessage>().eq(AsstMessage::getSessionId, sessionId));
            if (count >= properties.getSessionMaxMessages()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.session.message-limit-reached",
                        "会话消息数已达上限（" + properties.getSessionMaxMessages() + " 条），请新建会话",
                        properties.getSessionMaxMessages());
            }
        }
        AsstMessage message = new AsstMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        message.setTokens(tokens);
        message.setElapsedMs(elapsedMs);
        message.setDegraded(degraded);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 刷新会话更新时间（用于会话列表按活跃度排序）。
     *
     * @param session 会话
     */
    public void touch(AsstSession session) {
        sessionMapper.updateById(session);
    }

    /**
     * 我的会话分页（含助手名称冗余展示）。
     *
     * @param userId  当前用户
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<SessionVO> page(Long userId, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<AsstSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstSession::getUserId, userId);
        wrapper.orderByDesc(AsstSession::getUpdateTime).orderByDesc(AsstSession::getId);
        Page<AsstSession> page = sessionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, String> assistantNames = assistantNames(page.getRecords());
        List<SessionVO> records = new ArrayList<>(page.getRecords().size());
        for (AsstSession session : page.getRecords()) {
            records.add(toVo(session, assistantNames.get(session.getAssistantId())));
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 会话消息（仅本人）。
     *
     * @param sessionId 会话ID
     * @param userId    当前用户
     * @return 消息列表
     */
    public List<MessageVO> messages(Long sessionId, Long userId) {
        requireOwned(sessionId, userId);
        LambdaQueryWrapper<AsstMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstMessage::getSessionId, sessionId);
        wrapper.orderByAsc(AsstMessage::getId);
        List<AsstMessage> messages = messageMapper.selectList(wrapper);
        List<MessageVO> result = new ArrayList<>(messages.size());
        for (AsstMessage message : messages) {
            result.add(MessageVO.builder()
                    .id(String.valueOf(message.getId()))
                    .role(message.getRole())
                    .content(message.getContent())
                    .citations(parseCitations(message.getCitationsJson()))
                    .tokens(message.getTokens() == null ? 0 : message.getTokens())
                    .elapsedMs(message.getElapsedMs() == null ? 0 : message.getElapsedMs())
                    .degraded(message.getDegraded() != null && message.getDegraded() == 1)
                    .createTime(format(message.getCreateTime()))
                    .build());
        }
        return result;
    }

    /**
     * 删除会话及其消息（逻辑删除，仅本人）。
     *
     * @param sessionId 会话ID
     * @param userId    当前用户
     */
    public void delete(Long sessionId, Long userId) {
        requireOwned(sessionId, userId);
        sessionMapper.deleteById(sessionId);
        LambdaQueryWrapper<AsstMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstMessage::getSessionId, sessionId);
        messageMapper.delete(wrapper);
    }

    /**
     * 批量取助手名称。
     *
     * @param sessions 会话列表
     * @return 助手ID → 名称
     */
    private Map<Long, String> assistantNames(List<AsstSession> sessions) {
        Map<Long, String> names = new HashMap<>();
        for (AsstSession session : sessions) {
            Long assistantId = session.getAssistantId();
            if (assistantId == null || names.containsKey(assistantId)) {
                continue;
            }
            AsstDefinition definition = definitionService.requireExists(assistantId);
            names.put(assistantId, definition.getName());
        }
        return names;
    }

    /**
     * 实体转视图对象。
     *
     * @param session       会话
     * @param assistantName 助手名称（可空）
     * @return 视图对象
     */
    private SessionVO toVo(AsstSession session, String assistantName) {
        return SessionVO.builder()
                .id(String.valueOf(session.getId()))
                .assistantId(String.valueOf(session.getAssistantId()))
                .assistantName(assistantName)
                .title(session.getTitle())
                .source(session.getSource())
                .channelRef(session.getChannelRef())
                .createTime(format(session.getCreateTime()))
                .updateTime(format(session.getUpdateTime()))
                .build();
    }

    /**
     * 由问题生成会话标题。
     *
     * @param question 问题
     * @return 标题（去换行，截断 50 字）
     */
    public static String titleOf(String question) {
        String flat = question == null ? "" : question.replaceAll("\\s+", " ").trim();
        if (flat.isEmpty()) {
            return "新会话";
        }
        return flat.length() <= TITLE_LIMIT ? flat : flat.substring(0, TITLE_LIMIT);
    }

    /**
     * 解析引用 JSON。
     *
     * @param json 引用 JSON
     * @return 引用列表；空或解析失败返回空列表
     */
    public static List<CitationVO> parseCitations(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<CitationVO> citations = MAPPER.readValue(json, new TypeReference<List<CitationVO>>() {
            });
            return citations == null ? Collections.emptyList() : citations;
        } catch (JsonProcessingException e) {
            log.warn("引用 JSON 解析失败", e);
            return List.of();
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
