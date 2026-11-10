package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.DataQueryToolRequest;
import com.bluewhale.agent.service.DataQueryService;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能体只读数据工具接口（{@code /agent/tools/data-query}，L1）。
 *
 * <p>独立于 {@link AgentToolController} 的 L0 工具，单独要求 {@code sys:ai:data:read}；
 * SQL 守卫、行数上限与脱敏由 {@link DataQueryService} 统一执行。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent/tools")
@RequirePermission("sys:ai:data:read")
@RequiredArgsConstructor
public class AgentDataToolController {

    private final DataQueryService dataQueryService;

    /**
     * 只读数据查询工具。
     *
     * @param request SQL 入参
     * @return 执行 SQL、视图、行数与脱敏结果
     */
    @PostMapping("/data-query")
    @AuditLog(operation = "智能体数据查询", type = OperateType.QUERY, targetType = "ai_readonly_view")
    public Result<Map<String, Object>> dataQuery(@Valid @RequestBody DataQueryToolRequest request) {
        return Result.ok(dataQueryService.query(request.getSql()));
    }
}
