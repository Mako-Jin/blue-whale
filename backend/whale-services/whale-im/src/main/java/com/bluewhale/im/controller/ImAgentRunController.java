package com.bluewhale.im.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.im.dto.AgentConfirmRequest;
import com.bluewhale.im.service.ImAgentRunService;
import com.bluewhale.im.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能体运行确认接口（{@code /im/agent-runs}）。
 *
 * <p>权限口径：全类要求 {@code sys:im:chat}；数据口径：确认人必须是该会话成员，
 * 令牌归属与 {@code sys:ai:ops:*} 权限由 whale-agent 校验。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/im/agent-runs")
@RequirePermission("sys:im:chat")
@RequiredArgsConstructor
public class ImAgentRunController {

    private final ImAgentRunService runService;

    /**
     * 确认或拒绝一次待确认的写操作。
     *
     * @param id          运行记录ID
     * @param request     确认请求（缺省为执行）
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id} 与权限码）
     * @return 运行ID、最终状态与结果消息ID
     */
    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id,
                                               @RequestBody(required = false) AgentConfirmRequest request,
                                               HttpServletRequest httpRequest) {
        boolean approved = request == null || request.getApproved() == null
                || Boolean.TRUE.equals(request.getApproved());
        return Result.ok(runService.confirm(id, approved, UserContext.requireUserId(httpRequest),
                WebHeaders.header(httpRequest, WebHeaders.PERMISSION_CODES)));
    }
}
