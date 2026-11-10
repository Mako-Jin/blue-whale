package com.bluewhale.agent.controller;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.mcp.McpRequestHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MCP 接入端点（轨 F，本地只读）。
 *
 * <p>{@code POST /agent/mcp} 接收 MCP JSON-RPC 消息，凭 {@code X-MCP-Token}
 * 或 {@code Authorization: Bearer <token>} 鉴权；未配置 Token 时端点关闭（503）。
 * 设计上供 IDE 直连服务端口（8090），不经网关，避免与用户 JWT 体系耦合。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class McpController {

    /** 未启用时的错误码 */
    private static final int MCP_DISABLED = -32001;

    /** 鉴权失败时的错误码 */
    private static final int MCP_UNAUTHORIZED = -32002;

    private final AgentProperties properties;

    private final McpRequestHandler handler;

    /**
     * 处理 MCP 请求。
     *
     * @param token         {@code X-MCP-Token} 请求头
     * @param authorization {@code Authorization} 请求头
     * @param body          JSON-RPC 请求体
     * @return JSON-RPC 响应；通知消息返回 202
     */
    @PostMapping(value = "/agent/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handle(@RequestHeader(value = "X-MCP-Token", required = false) String token,
                                         @RequestHeader(value = "Authorization", required = false) String authorization,
                                         @RequestBody(required = false) String body) {
        AgentProperties.Mcp mcp = properties.getMcp();
        if (!mcp.isEnabled() || !StringUtils.hasText(mcp.getToken())) {
            return json(503, error(MCP_DISABLED, "MCP 端点未启用，请配置 whale.agent.mcp.token"));
        }
        String provided = StringUtils.hasText(token) ? token.trim() : bearer(authorization);
        if (!StringUtils.hasText(provided) || !constantEquals(provided, mcp.getToken())) {
            return json(401, error(MCP_UNAUTHORIZED, "MCP Token 无效"));
        }
        if (!StringUtils.hasText(body)) {
            return json(400, "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"请求体为空\"}}");
        }
        String response = handler.handle(body);
        if (response == null) {
            return ResponseEntity.status(202).build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }

    /**
     * 从 Authorization 头提取 Bearer Token。
     *
     * @param authorization 请求头
     * @return Token 或 null
     */
    private String bearer(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return null;
    }

    /**
     * 常量时间比较，避免时序侧信道。
     *
     * @param provided 客户端 Token
     * @param expected 配置 Token
     * @return 是否一致
     */
    private boolean constantEquals(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造带状态码的 JSON 响应。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return 响应
     */
    private ResponseEntity<String> json(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /**
     * 构造错误 JSON。
     *
     * @param code    错误码
     * @param message 错误说明
     * @return JSON
     */
    private String error(int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":" + code
                + ",\"message\":\"" + message + "\"}}";
    }
}
