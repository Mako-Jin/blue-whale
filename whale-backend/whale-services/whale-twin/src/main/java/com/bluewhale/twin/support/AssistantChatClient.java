package com.bluewhale.twin.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.config.TwinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * whale-assistant 开放 API 客户端：AI 解读问答（Wave 6.04 M1）。
 *
 * <p>调 {@code POST /assistant/openapi/v1/chat}（Wave 6.01 M2 开放 API）：
 * 经 {@link InternalTokenSupport} 自动附带 {@code X-Internal-Token}，
 * 并以 {@code X-User-Id}/{@code X-Tenant-Id} 透传最终用户与租户。</p>
 *
 * <p><b>失败策略</b>：与 {@link IotStateClient} 的旁路降级不同，解读是用户主动
 * 触发的主链路——失败须抛 {@link BizException} 让前端弹窗可见。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatClient {

    /** 开放问答路径 */
    private static final String CHAT_PATH = "/assistant/openapi/v1/chat";

    /** 成功业务码 */
    private static final String CODE_SUCCESS = "0";

    private final TwinProperties properties;

    /**
     * 同步问答。
     *
     * @param question   用户问题（含孪生上下文）
     * @param userId     最终用户ID
     * @param tenantId   租户ID（可空，缺省 1）
     * @param channelRef 业务侧会话引用（twin:{sceneId}:{entityId|-}）
     * @return 开放问答结果（answer/tokens/elapsedMillis/degraded 等键）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(String question, Long userId, String tenantId, String channelRef) {
        TwinProperties.Assistant config = properties.getAssistant();
        if (config.getAssistantCode() == null || config.getAssistantCode().isBlank()) {
            throw new BizException("未配置 AI 解读助手（whale.twin.assistant.assistant-code）");
        }
        Map<String, Object> body = Map.of(
                "assistantCode", config.getAssistantCode(),
                "question", question,
                "channelRef", channelRef);
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .post()
                    .uri(config.getBaseUrl() + CHAT_PATH)
                    .header(WebHeaders.USER_ID, String.valueOf(userId))
                    .header(WebHeaders.TENANT_ID, tenantId == null || tenantId.isBlank() ? "1" : tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object code = response == null ? null : response.get("code");
            if (code == null || !CODE_SUCCESS.equals(String.valueOf(code))) {
                log.warn("AI 解读问答业务失败：userId={}, code={}, message={}",
                        userId, code, response == null ? null : response.get("message"));
                throw new BizException("AI 解读服务响应异常，请稍后重试");
            }
            Object data = response.get("data");
            if (!(data instanceof Map)) {
                throw new BizException("AI 解读服务返回内容为空");
            }
            return (Map<String, Object>) data;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 解读问答调用失败：userId={}, reason={}", userId, e.getMessage());
            throw new BizException("AI 解读服务不可用，请稍后重试");
        }
    }
}
