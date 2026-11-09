package com.bluewhale.assistant.controller;

import com.bluewhale.assistant.dto.OpenChatRequest;
import com.bluewhale.assistant.dto.OpenChatVO;
import com.bluewhale.assistant.service.AssistantChatService;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手开放 API（业务应用服务对服务内嵌问答）。
 *
 * <p>鉴权：内网令牌 {@code X-Internal-Token}（由 starter 的 InternalTokenFilter 校验，
 * 路径 {@code /assistant/openapi/**} 已纳入保护）；不经网关，业务后端经 Nacos 直连本服务。
 * 最终用户身份以 {@code X-User-Id} 透传，权限码以 {@code X-Permission-Codes} 可选透传。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/assistant/openapi/v1")
public class AssistantOpenApiController {

    private final AssistantChatService chatService;

    /**
     * 开放问答（同步返回完整回答）。
     *
     * @param request         问答请求（assistantCode + question，可选 sessionId 续聊）
     * @param userId          业务侧透传的最终用户ID
     * @param permissionCodes 业务侧可选透传的权限码
     * @return 聚合后的回答（含引用、token、耗时）
     */
    @PostMapping("/chat")
    public Result<OpenChatVO> chat(@Valid @RequestBody OpenChatRequest request,
                                   @RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader(value = "X-Permission-Codes", required = false) String permissionCodes) {
        return Result.ok(chatService.chatSync(request, userId, permissionCodes));
    }
}
