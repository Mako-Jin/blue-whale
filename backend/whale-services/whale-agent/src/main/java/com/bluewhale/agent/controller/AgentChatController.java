package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.ChatRequest;
import com.bluewhale.agent.dto.FeedbackRequest;
import com.bluewhale.agent.service.AgentConversationService;
import com.bluewhale.agent.service.ChatService;
import com.bluewhale.agent.support.web.UserContext;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 问答接口：SSE 流式提问与回答评价。
 *
 * <p>事件约定：{@code meta}（会话、意图、证据清单）→ {@code delta}（增量文本）→
 * {@code done}（消息ID、token、耗时、引用）；异常时发 {@code error} 事件并结束。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentChatController {

    /** 运行时事实权限码 */
    private static final String RUNTIME_PERMISSION = "sys:agent:runtime:read";

    private final ChatService chatService;

    private final AgentConversationService conversationService;

    /**
     * 提问（SSE 流式）。
     *
     * @param request     提问请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return SSE 发射器
     */
    @PostMapping("/chat")
    @RequirePermission("sys:agent:chat")
    @AuditLog(operation = "知识问答", type = OperateType.QUERY, targetType = "agent_conversation")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        boolean includeRuntime = UserContext.hasPermission(httpRequest, RUNTIME_PERMISSION);
        return chatService.chat(request, UserContext.requireUserId(httpRequest), includeRuntime);
    }

    /**
     * 回答评价。
     *
     * @param request     评价请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @PostMapping("/feedback")
    public Result<Void> feedback(@Valid @RequestBody FeedbackRequest request, HttpServletRequest httpRequest) {
        conversationService.feedback(UserContext.requireUserId(httpRequest), request);
        return Result.ok();
    }
}
