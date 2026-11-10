package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.FactVO;
import com.bluewhale.agent.service.FactStoreService;
import com.bluewhale.agent.support.web.UserContext;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 结构化事实查询接口（{@code GET /agent/facts}）。
 *
 * <p>按 {@code type + key} 精确查询端口、路由、依赖版本、表字段、提交等事实，
 * 受 {@code sys:agent:search} 权限保护；运行时事实额外要求 {@code sys:agent:runtime:read}：
 * 无该权限时显式请求 {@code RUNTIME_*} 返回 403，未指定类型时自动剔除运行时事实。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentFactController {

    /** 运行时事实权限码 */
    private static final String RUNTIME_PERMISSION = "sys:agent:runtime:read";

    /** 运行时事实类型前缀 */
    private static final String RUNTIME_TYPE_PREFIX = "RUNTIME";

    private final FactStoreService factStoreService;

    /**
     * 查询结构化事实。
     *
     * @param type        事实类型（可选：SERVICE/ROUTE/PORT/DEPENDENCY/TABLE/COLUMN/COMMIT/ENDPOINT/RUNTIME_SERVICE）
     * @param key         事实键（可选，如服务名、表名、{@code groupId:artifactId}）
     * @param limit       返回条数（可选，默认 50，上限 500）
     * @param httpRequest 当前请求（读取网关注入的权限码）
     * @return 事实列表
     */
    @GetMapping("/facts")
    @RequirePermission("sys:agent:search")
    @AuditLog(operation = "事实查询", type = OperateType.QUERY, targetType = "agent_fact")
    public Result<List<FactVO>> facts(@RequestParam(value = "type", required = false) String type,
                                      @RequestParam(value = "key", required = false) String key,
                                      @RequestParam(value = "limit", required = false) Integer limit,
                                      HttpServletRequest httpRequest) {
        boolean runtimeAllowed = UserContext.hasPermission(httpRequest, RUNTIME_PERMISSION);
        if (!runtimeAllowed && StringUtils.hasText(type)
                && type.trim().toUpperCase().startsWith(RUNTIME_TYPE_PREFIX)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.ok(factStoreService.queryVo(type, key, limit, runtimeAllowed));
    }
}
