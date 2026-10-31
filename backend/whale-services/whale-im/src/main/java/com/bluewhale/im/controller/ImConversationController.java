package com.bluewhale.im.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.im.dto.AddMembersRequest;
import com.bluewhale.im.dto.BotToggleRequest;
import com.bluewhale.im.dto.CreateConversationRequest;
import com.bluewhale.im.dto.ReadRequest;
import com.bluewhale.im.dto.SendMessageRequest;
import com.bluewhale.im.dto.TransferOwnerRequest;
import com.bluewhale.im.service.ImBotService;
import com.bluewhale.im.service.ImConversationService;
import com.bluewhale.im.service.ImMemberService;
import com.bluewhale.im.service.ImMessageSendService;
import com.bluewhale.im.service.ImMessageService;
import com.bluewhale.im.support.web.UserContext;
import com.bluewhale.im.vo.ConversationVO;
import com.bluewhale.im.vo.MemberVO;
import com.bluewhale.im.vo.MessagePageVO;
import com.bluewhale.im.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private final ImMessageSendService messageSendService;

    private final ImMemberService memberService;

    private final ImBotService botService;

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
        return Result.ok(conversationService.createVo(UserContext.requireUserId(httpRequest), request));
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
        return Result.ok(messageSendService.send(id, UserContext.requireUserId(httpRequest), request,
                WebHeaders.header(httpRequest, WebHeaders.PERMISSION_CODES)));
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

    /**
     * 撤回消息（仅发送者本人，超时拒绝）。
     *
     * @param id          会话ID
     * @param messageId   消息ID
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @PostMapping("/{id}/messages/{messageId}/recall")
    public Result<Void> recall(@PathVariable Long id,
                               @PathVariable Long messageId,
                               HttpServletRequest httpRequest) {
        messageService.recall(id, UserContext.requireUserId(httpRequest), messageId);
        return Result.ok();
    }

    /**
     * 获取或初始化「当前用户 × 蓝鲸助手」的机器人会话（幂等）。
     *
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 机器人会话
     */
    @PostMapping("/bot")
    public Result<ConversationVO> openBotConversation(HttpServletRequest httpRequest) {
        return Result.ok(botService.openDefault(UserContext.requireUserId(httpRequest)));
    }

    /**
     * 群成员列表。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 成员列表
     */
    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(memberService.list(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 添加群成员（群主 / 管理员）。
     *
     * @param id          会话ID
     * @param request     待添加成员
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 实际新增成员
     */
    @PostMapping("/{id}/members")
    public Result<List<MemberVO>> addMembers(@PathVariable Long id,
                                             @Valid @RequestBody AddMembersRequest request,
                                             HttpServletRequest httpRequest) {
        return Result.ok(memberService.add(id, UserContext.requireUserId(httpRequest), request.getMemberIds()));
    }

    /**
     * 移除群成员（群主 / 管理员）。
     *
     * @param id          会话ID
     * @param userId      被移除用户ID
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id,
                                     @PathVariable Long userId,
                                     HttpServletRequest httpRequest) {
        memberService.remove(id, UserContext.requireUserId(httpRequest), userId);
        return Result.ok();
    }

    /**
     * 主动退群（群主不可退群）。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @PostMapping("/{id}/quit")
    public Result<Void> quit(@PathVariable Long id, HttpServletRequest httpRequest) {
        memberService.quit(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 转让群主（仅群主）。
     *
     * @param id          会话ID
     * @param request     转让请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @PostMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id,
                                 @Valid @RequestBody TransferOwnerRequest request,
                                 HttpServletRequest httpRequest) {
        conversationService.transfer(id, UserContext.requireUserId(httpRequest), request.getTargetUserId());
        return Result.ok();
    }

    /**
     * 解散群聊（仅群主，逻辑删除）。
     *
     * @param id          会话ID
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> dissolve(@PathVariable Long id, HttpServletRequest httpRequest) {
        conversationService.dissolve(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 启用或停用群聊机器人（群主 / 管理员）。
     *
     * @param id          会话ID
     * @param request     开关请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空结果
     */
    @PostMapping("/{id}/bot")
    public Result<Void> toggleBot(@PathVariable Long id,
                                  @Valid @RequestBody BotToggleRequest request,
                                  HttpServletRequest httpRequest) {
        botService.setGroupBotEnabled(id, UserContext.requireUserId(httpRequest),
                Boolean.TRUE.equals(request.getEnabled()));
        return Result.ok();
    }
}
