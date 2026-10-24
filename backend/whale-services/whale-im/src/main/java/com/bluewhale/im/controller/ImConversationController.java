package com.bluewhale.im.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.im.dto.CreateConversationRequest;
import com.bluewhale.im.dto.ReadRequest;
import com.bluewhale.im.dto.SendMessageRequest;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImMessage;
import com.bluewhale.im.service.ImConversationService;
import com.bluewhale.im.service.ImMessageService;
import com.bluewhale.im.support.web.UserContext;
import com.bluewhale.im.vo.ConversationVO;
import com.bluewhale.im.vo.MessagePageVO;
import com.bluewhale.im.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话与消息接口（{@code /im/conversations}）。
 *
 * <p>权限口径：全类要求 {@code sys:im:chat}（网关权限码头 + starter 拦截器判定，super_admin 放行）；
 * 数据口径：一律以网关 {@code X-User-Id} 为当前用户，非会话成员拒绝访问。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/im/conversations")
@RequirePermission("sys:im:chat")
@RequiredArgsConstructor
public class ImConversationController {

    private final ImConversationService conversationService;

    private final ImMessageService messageService;

    /**
     * 我的会话列表。
     *
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 会话列表（含未读数与最后一条消息摘要）
     */
    @GetMapping
    public Result<List<ConversationVO>> list(HttpServletRequest httpRequest) {
        return Result.ok(conversationService.list(UserContext.requireUserId(httpRequest)));
    }

    /**
     * 创建会话。
     *
     * @param request     创建请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 新会话
     */
    @PostMapping
    public Result<ConversationVO> create(@Valid @RequestBody CreateConversationRequest request,
                                         HttpServletRequest httpRequest) {
        ImConversation conversation = conversationService.create(UserContext.requireUserId(httpRequest), request);
        return Result.ok(ImConversationService.toVo(conversation, 0L, null));
    }

    /**
     * 历史消息游标拉取。
     *
     * @param id          会话ID
     * @param beforeSeq   取该序号之前（向前翻页，可空）
     * @param afterSeq    取该序号之后（断线补拉，可空且优先）
     * @param limit       条数上限（默认 30，最大 100）
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 升序消息页
     */
    @GetMapping("/{id}/messages")
    public Result<MessagePageVO> messages(@PathVariable Long id,
                                          @RequestParam(required = false) Long beforeSeq,
                                          @RequestParam(required = false) Long afterSeq,
                                          @RequestParam(defaultValue = "30") int limit,
                                          HttpServletRequest httpRequest) {
        Long userId = UserContext.requireUserId(httpRequest);
        return Result.ok(messageService.history(id, userId, beforeSeq, afterSeq, limit));
    }

    /**
     * 发送消息（{@code clientMsgId} 幂等）。
     *
     * @param id          会话ID
     * @param request     发送请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 已落库消息
     */
    @PostMapping("/{id}/messages")
    public Result<MessageVO> send(@PathVariable Long id,
                                  @Valid @RequestBody SendMessageRequest request,
                                  HttpServletRequest httpRequest) {
        ImMessage message = messageService.send(id, UserContext.requireUserId(httpRequest), request);
        return Result.ok(ImMessageService.toVo(message));
    }

    /**
     * 已读上报。
     *
     * @param id          会话ID
     * @param request     已读请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id,
                             @Valid @RequestBody ReadRequest request,
                             HttpServletRequest httpRequest) {
        conversationService.markRead(id, UserContext.requireUserId(httpRequest), request.getSeq());
        return Result.ok();
    }
}
