package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.PlatformOpRequest;
import com.bluewhale.agent.service.PlatformOpService;
import com.bluewhale.agent.support.web.UserContext;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 平台写操作接口（L2：创建确认令牌 / 确认执行 / 拒绝）。
 *
 * <p>三个接口统一要求 {@code sys:agent:chat} 作为入口权限；真正的写操作权限
 * （如 {@code sys:ai:ops:form}）在确认时由 {@link PlatformOpService} 校验。
 * 未确认的令牌不会触发任何下游写请求。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent")
@RequirePermission("sys:agent:chat")
@RequiredArgsConstructor
public class AgentOpsController {

    /** 操作对象类型 */
    private static final String TARGET_TYPE = "agent_operation";

    private final PlatformOpService platformOpService;

    /**
     * 创建平台操作确认令牌（Python 工具回调，不执行写操作）。
     *
     * @param request     操作与参数
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id}）
     * @return 确认卡片数据
     */
    @PostMapping("/tools/platform-op")
    @AuditLog(operation = "智能体平台操作待确认", type = OperateType.OTHER, targetType = TARGET_TYPE)
    public Result<Map<String, Object>> create(@Valid @RequestBody PlatformOpRequest request,
                                              HttpServletRequest httpRequest) {
        return Result.ok(platformOpService.create(request.getOperation(), request.getParams(),
                UserContext.requireUserId(httpRequest), request.getConversationId()));
    }

    /**
     * 确认并执行平台操作（一次性）。
     *
     * @param confirmationId 确认令牌ID
     * @param httpRequest    当前请求（读取网关 {@code X-User-Id} 与权限码）
     * @return 下游响应数据
     */
    @PostMapping("/ops/{confirmationId}/confirm")
    @AuditLog(operation = "智能体平台操作确认执行", type = OperateType.OTHER,
            targetType = TARGET_TYPE, targetId = "#confirmationId")
    public Result<Map<String, Object>> confirm(@PathVariable String confirmationId,
                                               HttpServletRequest httpRequest) {
        return Result.ok(platformOpService.confirm(confirmationId, UserContext.requireUserId(httpRequest),
                WebHeaders.header(httpRequest, WebHeaders.PERMISSION_CODES)));
    }

    /**
     * 拒绝执行平台操作。
     *
     * @param confirmationId 确认令牌ID
     * @param httpRequest    当前请求（读取网关 {@code X-User-Id}）
     * @return 最新状态
     */
    @PostMapping("/ops/{confirmationId}/reject")
    @AuditLog(operation = "智能体平台操作拒绝", type = OperateType.OTHER,
            targetType = TARGET_TYPE, targetId = "#confirmationId")
    public Result<Map<String, Object>> reject(@PathVariable String confirmationId,
                                              HttpServletRequest httpRequest) {
        return Result.ok(platformOpService.reject(confirmationId, UserContext.requireUserId(httpRequest)));
    }
}
