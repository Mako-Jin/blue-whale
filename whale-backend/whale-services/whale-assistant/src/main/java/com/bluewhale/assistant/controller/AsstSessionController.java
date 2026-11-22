package com.bluewhale.assistant.controller;

import com.bluewhale.assistant.dto.ChatRequest;
import com.bluewhale.assistant.dto.MessageVO;
import com.bluewhale.assistant.dto.SessionCreateRequest;
import com.bluewhale.assistant.dto.SessionVO;
import com.bluewhale.assistant.service.AssistantChatService;
import com.bluewhale.assistant.service.AsstSessionService;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 会话接口：创建会话、我的会话分页、历史消息、删除与流式问答（均仅本人可见）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/assistant/v{version}/sessions", version = "1")
@RequiredArgsConstructor
public class AsstSessionController {

    private final AsstSessionService sessionService;

    private final AssistantChatService chatService;

    /**
     * 创建会话（助手必须已发布）。
     *
     * @param request     创建请求
     * @param httpRequest 当前请求
     * @return 会话视图
     */
    @PostMapping
    @RequirePermission("sys:assistant:chat")
    public Result<SessionVO> create(@Valid @RequestBody SessionCreateRequest request,
                                    HttpServletRequest httpRequest) {
        return Result.ok(sessionService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 我的会话分页。
     *
     * @param current     当前页（默认 1）
     * @param size        每页条数（默认 20，上限 100）
     * @param httpRequest 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:assistant:chat")
    public Result<PageResult<SessionVO>> page(@RequestParam(value = "current", defaultValue = "1") long current,
                                              @RequestParam(value = "size", defaultValue = "20") long size,
                                              HttpServletRequest httpRequest) {
        return Result.ok(sessionService.page(UserContext.requireUserId(httpRequest), current, size));
    }

    /**
     * 会话消息（含引用块）。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求
     * @return 消息列表
     */
    @GetMapping("/{id}/messages")
    @RequirePermission("sys:assistant:chat")
    public Result<List<MessageVO>> messages(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        return Result.ok(sessionService.messages(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 删除会话及其消息。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:assistant:chat")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        sessionService.delete(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 流式问答（SSE）：事件序列 meta、tool（多次）、delta（多次）、done，异常下发 error。
     *
     * @param id          会话ID
     * @param request     提问请求
     * @param httpRequest 当前请求
     * @return SSE 发射器
     */
    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("sys:assistant:chat")
    @AuditLog(operation = "助手问答", type = OperateType.QUERY, targetType = "asst_session")
    public SseEmitter stream(@PathVariable("id") Long id,
                             @Valid @RequestBody ChatRequest request,
                             HttpServletRequest httpRequest) {
        return chatService.chat(id, request,
                UserContext.requireUserId(httpRequest),
                UserContext.permissionCodes(httpRequest) == null
                        ? null : String.join(",", UserContext.permissionCodes(httpRequest)));
    }
}
