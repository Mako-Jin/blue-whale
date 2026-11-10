package com.bluewhale.im.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.im.service.WsTicketService;
import com.bluewhale.im.support.web.UserContext;
import com.bluewhale.im.vo.WsTicketVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 凭证接口（{@code /im/ws-ticket}）。
 *
 * <p>本接口走标准 JWT + {@code sys:im:chat} 权限；签发的 ticket 仅用于 {@code /im/ws} 握手一次，
 * 浏览器 WS 不在 URL 携带 JWT。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/im")
@RequirePermission("sys:im:chat")
@RequiredArgsConstructor
public class ImWsController {

    private final WsTicketService ticketService;

    /**
     * 换取一次性 WS ticket。
     *
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return ticket 与有效期
     */
    @PostMapping("/ws-ticket")
    public Result<WsTicketVO> ticket(HttpServletRequest httpRequest) {
        return Result.ok(ticketService.issue(UserContext.requireUserId(httpRequest)));
    }
}
