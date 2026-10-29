package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.interfaces.dto.DocWsTicketRequest;
import com.bluewhale.doc.interfaces.vo.DocWsTicketVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档实时协作接口（M2）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocCollabController {

    private final DocCollabService collabService;

    /**
     * 签发一次性协作 WS ticket。
     *
     * <p>浏览器无法在 WebSocket 握手携带 Authorization 头，因此先用标准 JWT 调用本接口，
     * 再用 60 秒 ticket 升级 {@code /doc/ws}，避免 JWT 出现在 URL 中。</p>
     *
     * @param request     页面ID
     * @param httpRequest 当前请求
     * @return ticket 与有效期
     */
    @PostMapping("/ws-ticket")
    @RequirePermission("sys:doc:page:edit")
    public Result<DocWsTicketVO> ticket(@Valid @RequestBody DocWsTicketRequest request,
                                        HttpServletRequest httpRequest) {
        Long userId = UserContext.requireUserId(httpRequest);
        return Result.ok(collabService.issueTicket(request.getPageId(), userId,
                WebHeaders.username(httpRequest)));
    }
}
