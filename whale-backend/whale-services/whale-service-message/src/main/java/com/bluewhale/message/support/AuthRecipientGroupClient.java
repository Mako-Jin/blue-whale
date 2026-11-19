package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.message.config.MessageRecipientProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * auth 接收人组内网客户端：按角色编码拉取启用用户 ID。
 *
 * <p>调用 {@code GET /user/v1/internal/by-role}（网关 403、auth permitAll、
 * X-Internal-Token），带 {@code X-Tenant-Id} 租户上下文。与偏好解析不同，
 * 接收人是投递主目标，调用失败必须拒绝发送而不是静默发空。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AuthRecipientGroupClient {

    /** auth 按角色查询路径 */
    private static final String BY_ROLE_PATH = "/user/v1/internal/by-role";

    private final MessageRecipientProperties properties;

    private final ObjectMapper objectMapper;

    /** 测试注入的客户端；为空时按内网令牌现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：客户端按 {@link InternalTokenSupport} 当前配置现场创建。
     *
     * @param properties   接收人组配置
     * @param objectMapper JSON 映射器
     */
    @Autowired
    public AuthRecipientGroupClient(MessageRecipientProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    /**
     * 测试用构造：外部传入 RestClient。
     *
     * @param properties   接收人组配置
     * @param objectMapper JSON 映射器
     * @param client       调用客户端（为空表示按内网令牌现场创建）
     */
    AuthRecipientGroupClient(MessageRecipientProperties properties, ObjectMapper objectMapper,
                             RestClient client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    /**
     * 按角色编码查询成员用户 ID。
     *
     * @param tenantId 租户 ID（必须）
     * @param roleCode 角色编码
     * @return 启用用户 ID 列表；角色不存在时为空列表
     * @throws BizException auth 不可用或响应不可解析时抛出，避免静默发空
     */
    public List<Long> listMemberUserIds(Long tenantId, String roleCode) {
        if (tenantId == null || !StringUtils.hasText(roleCode)) {
            return List.of();
        }
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            String raw = target.get()
                    .uri(properties.getAuthBaseUrl() + BY_ROLE_PATH + "?roleCode={roleCode}", roleCode)
                    .headers(headers -> {
                        headers.set(InternalTokenSupport.headerName(), InternalTokenSupport.token());
                        headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                    })
                    .retrieve()
                    .body(String.class);
            return parseUserIds(raw);
        } catch (RuntimeException error) {
            log.warn("接收人组解析失败: group={}, status={}, errorType={}",
                    roleCode, httpStatus(error), error.getClass().getSimpleName());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "message.recipient-group.resolve-failed",
                    "接收人组解析失败: " + roleCode, roleCode);
        }
    }

    /**
     * 解析 auth 响应信封，提取用户 ID。
     *
     * @param raw 原始响应体
     * @return 用户 ID 列表
     */
    private List<Long> parseUserIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode code = root.path("code");
            if (!code.isMissingNode() && code.asInt(-1) != 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "message.recipient-group.resolve-failed",
                        "接收人组解析失败: " + root.path("message").asText(""), root.path("message").asText(""));
            }
            List<Long> userIds = new ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    long userId = item.path("id").asLong(-1);
                    if (userId > 0) {
                        userIds.add(userId);
                    }
                }
            }
            return userIds;
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            log.warn("接收人组响应解析失败: errorType={}", error.getClass().getSimpleName());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "message.recipient-group.parse-failed", "接收人组解析失败");
        }
    }

    /**
     * 提取 HTTP 状态码；非 HTTP 异常返回 {@code "-"}。
     *
     * @param error 调用异常
     * @return 状态码文本
     */
    private static String httpStatus(RuntimeException error) {
        if (error instanceof RestClientResponseException responseException) {
            return String.valueOf(responseException.getStatusCode().value());
        }
        return "-";
    }
}
