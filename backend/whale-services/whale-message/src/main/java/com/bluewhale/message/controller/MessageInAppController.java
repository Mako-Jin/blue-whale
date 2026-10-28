package com.bluewhale.message.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.message.dto.InAppMessageQueryRequest;
import com.bluewhale.message.service.MessageInAppService;
import com.bluewhale.message.support.web.UserContext;
import com.bluewhale.message.vo.InAppMessageVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信收件箱接口。
 *
 * <p>服务内路径 {@code /message/inapp/**}，经网关按 {@code Path=/message/**} 转发。
 * 与运营查询不同，收件箱是登录用户的自助能力：不额外要求 {@code sys:message:*} 权限，
 * 但强依赖网关注入的 {@code X-User-Id}，只能读写本人消息。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/message/inapp")
@RequiredArgsConstructor
public class MessageInAppController {

    private final MessageInAppService messageInAppService;

    /**
     * 分页查询本人站内信。
     *
     * @param request     查询条件
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<InAppMessageVO>> page(InAppMessageQueryRequest request,
                                                   HttpServletRequest httpRequest) {
        return Result.ok(messageInAppService.page(UserContext.requireUserId(httpRequest), request));
    }

    /**
     * 查询本人未读站内信数量。
     *
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpServletRequest httpRequest) {
        return Result.ok(messageInAppService.unreadCount(UserContext.requireUserId(httpRequest)));
    }

    /**
     * 标记本人某条站内信为已读。
     *
     * @param messageId   消息ID
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 空响应体
     */
    @PostMapping("/{messageId}/read")
    public Result<Void> markRead(@PathVariable Long messageId, HttpServletRequest httpRequest) {
        messageInAppService.markRead(UserContext.requireUserId(httpRequest), messageId);
        return Result.ok();
    }
}
