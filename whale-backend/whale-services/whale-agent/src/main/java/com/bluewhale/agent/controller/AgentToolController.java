package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.DocReadToolRequest;
import com.bluewhale.agent.dto.FactGetToolRequest;
import com.bluewhale.agent.dto.KnowledgeSearchToolRequest;
import com.bluewhale.agent.support.mcp.McpToolService;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能体只读工具接口（{@code /agent/tools}，供 Python 编排服务内网调用）。
 *
 * <p>三个工具均复用 {@link McpToolService} 的检索、事实与文件读取内核，
 * 统一要求 {@code sys:agent:chat}；运行时事实不在此暴露（{@code RUNTIME_*} 由
 * {@link McpToolService} 拒绝）。调用方需透传用户权限码，审计按 QUERY 记录。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/agent/v{version}/tools", version = "1")
@RequirePermission("sys:agent:chat")
@RequiredArgsConstructor
public class AgentToolController {

    private final McpToolService toolService;

    /**
     * 知识检索工具。
     *
     * @param request 检索入参
     * @return 命中列表（query/count/items）
     */
    @PostMapping("/knowledge-search")
    @AuditLog(operation = "智能体知识检索", type = OperateType.QUERY, targetType = "agent_index")
    public Result<Map<String, Object>> knowledgeSearch(@Valid @RequestBody KnowledgeSearchToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", request.getQuery());
        if (request.getTopK() != null) {
            arguments.put("topK", request.getTopK());
        }
        if (request.getKinds() != null) {
            arguments.put("kinds", request.getKinds());
        }
        return Result.ok(toolService.searchKnowledge(arguments));
    }

    /**
     * 结构化事实查询工具。
     *
     * @param request 事实入参
     * @return 事实列表（count/items）
     */
    @PostMapping("/fact-get")
    @AuditLog(operation = "智能体事实查询", type = OperateType.QUERY, targetType = "agent_fact")
    public Result<Map<String, Object>> factGet(@Valid @RequestBody FactGetToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (request.getType() != null) {
            arguments.put("type", request.getType());
        }
        if (request.getKey() != null) {
            arguments.put("key", request.getKey());
        }
        if (request.getLimit() != null) {
            arguments.put("limit", request.getLimit());
        }
        return Result.ok(toolService.getFact(arguments));
    }

    /**
     * 文档读取工具。
     *
     * @param request 读取入参
     * @return 文件片段（path/startLine/endLine/totalLines/truncated/content）
     */
    @PostMapping("/doc-read")
    @AuditLog(operation = "智能体文档读取", type = OperateType.QUERY, targetType = "agent_index")
    public Result<Map<String, Object>> docRead(@Valid @RequestBody DocReadToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("path", request.getPath());
        if (request.getStartLine() != null) {
            arguments.put("startLine", request.getStartLine());
        }
        if (request.getEndLine() != null) {
            arguments.put("endLine", request.getEndLine());
        }
        return Result.ok(toolService.readFile(arguments));
    }
}
