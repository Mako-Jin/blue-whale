package com.bluewhale.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.agent.dto.CitationVO;
import com.bluewhale.agent.dto.ConversationVO;
import com.bluewhale.agent.dto.FeedbackRequest;
import com.bluewhale.agent.dto.MessageVO;
import com.bluewhale.agent.entity.AgentConversation;
import com.bluewhale.agent.entity.AgentFeedback;
import com.bluewhale.agent.entity.AgentMessage;
import com.bluewhale.agent.mapper.AgentConversationMapper;
import com.bluewhale.agent.mapper.AgentFeedbackMapper;
import com.bluewhale.agent.mapper.AgentMessageMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 会话服务：会话归属校验、创建、消息落库、历史读取、分页查询、删除与评价。
 *
 * <p>所有读写均以网关 {@code X-User-Id} 为隔离条件，禁止跨用户访问。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 会话标题最大长度 */
    private static final int TITLE_LIMIT = 50;

    /** 检索范围可选值 */
    private static final Set<String> SCOPE_VALUES = Set.of(
            "code", "doc", "sql", "config", "git", "runtime", "file", "archive", "doc_page");

    /** 角色：用户 */
    public static final String ROLE_USER = "user";

    /** 角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";

    private final AgentConversationMapper conversationMapper;

    private final AgentMessageMapper messageMapper;

    private final AgentFeedbackMapper feedbackMapper;

    /**
     * 解析或新建会话。
     *
     * @param conversationId 会话ID（可空）
     * @param userId         当前用户
     * @param question       首个问题（新建时用于生成标题）
     * @param scope          检索范围
     * @return 会话
     */
    public AgentConversation resolve(Long conversationId, Long userId, String question, String scope) {
        String normalizedScope = normalizeScope(scope);
        if (conversationId != null) {
            AgentConversation conversation = requireOwned(conversationId, userId);
            if (normalizedScope != null && !normalizedScope.equals(conversation.getScope())) {
                conversation.setScope(normalizedScope);
                conversationMapper.updateById(conversation);
            }
            return conversation;
        }
        AgentConversation conversation = new AgentConversation();
        conversation.setUserId(userId);
        conversation.setTitle(titleOf(question));
        conversation.setScope(normalizedScope);
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 追加消息。
     *
     * @param conversationId 会话ID
     * @param role           角色
     * @param content        正文
     * @param citationsJson  引用 JSON（用户消息为空）
     * @param tokens         token 统计
     * @return 落库消息
     */
    public AgentMessage append(Long conversationId, String role, String content, String citationsJson, int tokens) {
        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        message.setTokens(tokens);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 读取最近历史消息（按时间正序）。
     *
     * @param conversationId 会话ID
     * @param limit          条数上限
     * @return 历史消息
     */
    public List<AgentMessage> recent(Long conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<AgentMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMessage::getConversationId, conversationId);
        wrapper.orderByDesc(AgentMessage::getId);
        wrapper.last("LIMIT " + limit);
        List<AgentMessage> messages = new ArrayList<>(messageMapper.selectList(wrapper));
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 刷新会话更新时间（用于会话列表按活跃度排序）。
     *
     * @param conversation 会话
     */
    public void touch(AgentConversation conversation) {
        conversationMapper.updateById(conversation);
    }

    /**
     * 我的会话分页。
     *
     * @param userId  当前用户
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<ConversationVO> page(Long userId, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<AgentConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentConversation::getUserId, userId);
        wrapper.orderByDesc(AgentConversation::getUpdateTime).orderByDesc(AgentConversation::getId);
        Page<AgentConversation> page = conversationMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ConversationVO> records = new ArrayList<>(page.getRecords().size());
        for (AgentConversation conversation : page.getRecords()) {
            records.add(ConversationVO.builder()
                    .id(String.valueOf(conversation.getId()))
                    .title(conversation.getTitle())
                    .scope(conversation.getScope())
                    .createTime(format(conversation.getCreateTime()))
                    .updateTime(format(conversation.getUpdateTime()))
                    .build());
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 会话消息（仅本人）。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户
     * @return 消息列表
     */
    public List<MessageVO> messages(Long conversationId, Long userId) {
        requireOwned(conversationId, userId);
        LambdaQueryWrapper<AgentMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMessage::getConversationId, conversationId);
        wrapper.orderByAsc(AgentMessage::getId);
        List<AgentMessage> messages = messageMapper.selectList(wrapper);
        List<MessageVO> result = new ArrayList<>(messages.size());
        for (AgentMessage message : messages) {
            result.add(MessageVO.builder()
                    .id(String.valueOf(message.getId()))
                    .role(message.getRole())
                    .content(message.getContent())
                    .citations(parseCitations(message.getCitationsJson()))
                    .tokens(message.getTokens())
                    .createTime(format(message.getCreateTime()))
                    .build());
        }
        return result;
    }

    /**
     * 删除会话及其消息（逻辑删除，仅本人）。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户
     */
    public void delete(Long conversationId, Long userId) {
        requireOwned(conversationId, userId);
        conversationMapper.deleteById(conversationId);
        LambdaQueryWrapper<AgentMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);
    }

    /**
     * 保存回答评价（同一用户对同一消息重复评价时覆盖）。
     *
     * @param userId  当前用户
     * @param request 评价请求
     */
    public void feedback(Long userId, FeedbackRequest request) {
        Integer rating = request.getRating();
        if (rating == null || (rating != 1 && rating != -1)) {
            throw new BizException("评价取值非法，仅支持 1 或 -1");
        }
        AgentMessage message = messageMapper.selectById(request.getMessageId());
        if (message == null || !ROLE_ASSISTANT.equals(message.getRole())) {
            throw new BizException("被评价的消息不存在");
        }
        requireOwned(message.getConversationId(), userId);
        LambdaQueryWrapper<AgentFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentFeedback::getMessageId, request.getMessageId());
        wrapper.eq(AgentFeedback::getUserId, userId);
        AgentFeedback existing = feedbackMapper.selectOne(wrapper);
        if (existing == null) {
            AgentFeedback feedback = new AgentFeedback();
            feedback.setMessageId(request.getMessageId());
            feedback.setUserId(userId);
            feedback.setRating(rating);
            feedback.setComment(request.getComment());
            feedbackMapper.insert(feedback);
            return;
        }
        existing.setRating(rating);
        existing.setComment(request.getComment());
        feedbackMapper.updateById(existing);
    }

    /**
     * 校验会话归属。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户
     * @return 会话
     */
    private AgentConversation requireOwned(Long conversationId, Long userId) {
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new BizException("会话不存在或无权访问");
        }
        return conversation;
    }

    /**
     * 归一化检索范围：仅保留已知取值，去重后用逗号拼接。
     *
     * @param scope 原始范围
     * @return 归一化范围；入参为空返回 {@code null}
     */
    public static String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (String part : scope.split(",")) {
            String value = part.trim().toLowerCase();
            if (SCOPE_VALUES.contains(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        String joined = String.join(",", values);
        return joined.length() > 128 ? joined.substring(0, 128) : joined;
    }

    /**
     * 由问题生成会话标题。
     *
     * @param question 问题
     * @return 标题（去换行，截断 50 字）
     */
    private static String titleOf(String question) {
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
            return MAPPER.readValue(json, new TypeReference<List<CitationVO>>() {
            });
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
