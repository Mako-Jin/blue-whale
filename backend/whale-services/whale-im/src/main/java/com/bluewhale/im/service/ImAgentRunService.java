package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.im.entity.ImAgentRun;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.mapper.ImAgentRunMapper;
import com.bluewhale.im.support.AgentStreamClient;
import com.bluewhale.im.ws.ImMessageBroadcaster;
import com.bluewhale.im.ws.WsFrames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能体写操作确认：校验会话成员与运行状态，代理到 whale-agent 一次性令牌接口，
 * 回写运行结果并推送结果消息与帧。
 *
 * <p>安全口径：确认人必须是会话成员；令牌归属、权限码与过期由 whale-agent 统一校验，
 * 本服务不缓存也不提前执行任何写操作。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImAgentRunService {

    /** 运行状态：等待确认 */
    public static final String STATUS_WAITING_CONFIRM = "WAITING_CONFIRM";

    /** 运行状态：成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 运行状态：失败（含用户拒绝） */
    private static final String STATUS_FAILED = "FAILED";

    /** 发送者类型：机器人 */
    private static final String SENDER_BOT = "BOT";

    /** 内容类型：文本 */
    private static final String CONTENT_TEXT = "TEXT";

    private final ImAgentRunMapper runMapper;

    private final ImConversationGuard guard;

    private final AgentStreamClient agentStreamClient;

    private final ImAgentChatService chatService;

    private final ImBotService botService;

    private final ImMessageBroadcaster broadcaster;

    private final ObjectMapper objectMapper;

    /**
     * 确认或拒绝一次待确认的写操作。
     *
     * @param runId           运行记录ID
     * @param approved        {@code true} 执行；{@code false} 拒绝
     * @param userId          当前用户ID
     * @param permissionCodes 当前用户权限码
     * @return 运行ID、最终状态与结果消息ID
     */
    public Map<String, Object> confirm(Long runId, boolean approved, Long userId, String permissionCodes) {
        ImAgentRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException("运行记录不存在");
        }
        guard.requireMember(run.getConversationId(), userId);
        if (!STATUS_WAITING_CONFIRM.equals(run.getStatus())) {
            throw new BizException("该运行不在待确认状态");
        }
        JsonNode card = readCard(run.getConfirmationJson());
        String confirmationId = card.path("confirmationId").asText("");
        String operation = card.path("operation").asText("");
        if (!StringUtils.hasText(confirmationId)) {
            throw new BizException("确认信息缺失，请重新发起");
        }
        try {
            JsonNode data = agentStreamClient.confirmOperation(confirmationId, approved, userId, permissionCodes);
            String content = approved
                    ? "平台操作已执行：" + operation + "\n\n```json\n" + data + "\n```"
                    : "已拒绝执行平台操作：" + operation;
            return complete(run, approved ? STATUS_SUCCESS : STATUS_FAILED, content);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return complete(run, STATUS_FAILED, "平台操作确认被中断");
        } catch (IOException | RuntimeException e) {
            log.warn("平台操作确认失败: runId={}, confirmationId={}", runId, confirmationId, e);
            return complete(run, STATUS_FAILED, "平台操作执行失败：" + e.getMessage());
        }
    }

    /**
     * 解析确认卡片 JSON。
     *
     * @param json 卡片 JSON
     * @return 卡片节点
     */
    private JsonNode readCard(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException("确认信息缺失，请重新发起");
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            throw new BizException("确认信息解析失败，请重新发起");
        }
    }

    /**
     * 回写运行结果、落结果消息并广播。
     *
     * @param run     运行记录
     * @param status  最终状态
     * @param content 结果消息内容
     * @return 运行ID、状态与消息ID
     */
    private Map<String, Object> complete(ImAgentRun run, String status, String content) {
        Long conversationId = run.getConversationId();
        ImMessage message = chatService.appendMessage(conversationId, botService.botUserId(),
                SENDER_BOT, CONTENT_TEXT, content);
        LambdaUpdateWrapper<ImAgentRun> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImAgentRun::getId, run.getId());
        wrapper.set(ImAgentRun::getStatus, status);
        wrapper.set(ImAgentRun::getResponseMessageId, message.getId());
        wrapper.set(ImAgentRun::getFinishedAt, LocalDateTime.now());
        runMapper.update(null, wrapper);
        broadcaster.broadcast(conversationId, ImMessageService.toVo(message));
        broadcaster.broadcastFrame(conversationId, WsFrames.agentDone(conversationId, run.getId(), message.getId(),
                0, 0L, false));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", String.valueOf(run.getId()));
        result.put("status", status);
        result.put("messageId", String.valueOf(message.getId()));
        return result;
    }
}
