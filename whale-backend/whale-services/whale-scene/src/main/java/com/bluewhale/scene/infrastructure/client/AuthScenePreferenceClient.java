package com.bluewhale.scene.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.scene.application.port.ScenePreferencePort;
import com.bluewhale.scene.application.support.SceneViewModes;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.config.SceneProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 场景偏好内网客户端：读写 auth 统一配置 {@code sys_user_config}（Wave 6.25）。
 *
 * <p>配置键固定 {@code scene.view}，值形如
 * {@code {"viewMode":"2D","defaultSceneKey":"city-wall"}}。auth 只存不解释，
 * 枚举与「场景已发布」等业务校验留在 {@code ScenePreferenceAppServiceImpl}。</p>
 *
 * <p>失败语义刻意不对称：</p>
 * <ul>
 *   <li><b>读 fail-open</b>——网络/解析/业务码失败一律按「无配置」返回 empty 并记 WARN，
 *       由上层回落默认偏好。偏好是旁路，不该让用户进不去场景页。</li>
 *   <li><b>写 fail-closed</b>——写失败向上抛业务异常，绝不假装成功（否则用户以为存了，
 *       下次进来又变回去）。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AuthScenePreferenceClient implements ScenePreferencePort {

    /** 用户偏好在 auth 配置表中的键（反域名小写点号口径，见 auth ConfigKeyValidator） */
    public static final String CONFIG_KEY = "scene.view";

    /** auth 内网批量读取路径 */
    private static final String BATCH_GET_PATH = "/user/v1/internal/config/batch-get";

    /** auth 内网单条写入路径（Wave 6.25 新增） */
    private static final String SET_PATH = "/user/v1/internal/config/set";

    /** JSON 映射器（Jackson 3，无状态复用） */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /** 场景服务配置（取 auth 内网基地址） */
    private final SceneProperties properties;

    /** 测试注入的客户端；为空时按内网令牌现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：客户端按 {@link InternalTokenSupport} 当前配置现场创建。
     *
     * @param properties 场景服务配置
     */
    @Autowired
    public AuthScenePreferenceClient(SceneProperties properties) {
        this(properties, null);
    }

    /**
     * 测试用构造：外部传入 RestClient。
     *
     * @param properties 场景服务配置
     * @param client     调用客户端（为空表示按内网令牌现场创建）
     */
    AuthScenePreferenceClient(SceneProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 读取用户场景偏好。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 偏好视图；未配置或读取失败返回 {@link Optional#empty()}（调用方回落默认）
     */
    @Override
    public Optional<ScenePreferenceView> read(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return Optional.empty();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId);
        body.put("userIds", List.of(userId));
        body.put("keys", List.of(CONFIG_KEY));
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            String raw = target.post()
                    .uri(properties.getAuthBaseUrl() + BATCH_GET_PATH)
                    .headers(headers -> {
                        headers.set(InternalTokenSupport.headerName(), InternalTokenSupport.token());
                        headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseValue(raw, userId);
        } catch (RuntimeException error) {
            log.warn("场景偏好读取失败（按无配置处理）：tenantId={}, userId={}, reason={}",
                    tenantId, userId, error.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写入（覆盖）用户场景偏好。
     *
     * @param tenantId        租户 ID
     * @param userId          用户 ID
     * @param preference      偏好视图（viewMode 必须已校验）
     * @param operatorId      操作人用户 ID
     */
    @Override
    public void write(Long tenantId, Long userId, ScenePreferenceView preference, Long operatorId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("viewMode", preference.getViewMode());
        value.put("defaultSceneKey", preference.getDefaultSceneKey());
        String configValue;
        try {
            configValue = JSON_MAPPER.writeValueAsString(value);
        } catch (RuntimeException error) {
            throw new BizException(ErrorCode.PARAM_ERROR, "scene.preference.serialize-failed",
                    "场景偏好序列化失败：" + error.getMessage(), error.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("userId", userId);
        body.put("configKey", CONFIG_KEY);
        body.put("configValue", configValue);
        body.put("operatorId", operatorId);
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        String raw;
        try {
            raw = target.post()
                    .uri(properties.getAuthBaseUrl() + SET_PATH)
                    .headers(headers -> {
                        headers.set(InternalTokenSupport.headerName(), InternalTokenSupport.token());
                        headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException error) {
            log.error("场景偏好写入失败：tenantId={}, userId={}, reason={}",
                    tenantId, userId, error.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "scene.preference.save-failed",
                    "场景偏好保存失败，请稍后重试");
        }
        requireSuccess(raw, "场景偏好保存失败");
    }

    /**
     * 解析批量读取响应，取出当前用户的偏好值。
     *
     * @param raw    原始响应体
     * @param userId 目标用户 ID
     * @return 偏好视图；未命中或结构不符返回 empty
     */
    private Optional<ScenePreferenceView> parseValue(String raw, Long userId) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(raw);
        } catch (RuntimeException error) {
            log.warn("场景偏好响应解析失败（按无配置处理）：reason={}", error.getMessage());
            return Optional.empty();
        }
        if (!isSuccess(root)) {
            log.warn("场景偏好读取业务失败：{}", text(root.path("message")));
            return Optional.empty();
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return Optional.empty();
        }
        for (JsonNode item : data) {
            if (item.path("userId").asLong(-1) != userId) {
                continue;
            }
            JsonNode value = item.path("value");
            if (value.isNull() || value.isMissingNode() || !value.isTextual()) {
                return Optional.empty();
            }
            return parsePreference(value.asText());
        }
        return Optional.empty();
    }

    /**
     * 解析偏好 JSON 文本。
     *
     * @param json 配置值 JSON
     * @return 偏好视图；非法 JSON 或非对象返回 empty（按无配置处理）
     */
    private Optional<ScenePreferenceView> parsePreference(String json) {
        try {
            JsonNode node = JSON_MAPPER.readTree(json);
            if (!node.isObject()) {
                log.warn("场景偏好配置值不是 JSON 对象，按无配置处理：{}", json);
                return Optional.empty();
            }
            ScenePreferenceView view = new ScenePreferenceView();
            String viewMode = text(node.path("viewMode"));
            // 存储值可能被人工改坏，非法枚举一律回落默认，不让脏数据把用户卡死在错误视图
            view.setViewMode(SceneViewModes.isValid(viewMode) ? viewMode : SceneViewModes.VIEW_MODE_2D);
            view.setDefaultSceneKey(text(node.path("defaultSceneKey")));
            return Optional.of(view);
        } catch (RuntimeException error) {
            log.warn("场景偏好配置值解析失败（按无配置处理）：reason={}", error.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 校验写入响应业务码。
     *
     * @param raw          原始响应体
     * @param errorMessage 失败文案前缀
     */
    private void requireSuccess(String raw, String errorMessage) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(raw);
        } catch (RuntimeException error) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.scene.infrastructure.response-cannot-parse", "{0}（响应无法解析）", errorMessage);
        }
        if (!isSuccess(root)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    errorMessage + "：" + text(root.path("message")));
        }
    }

    /**
     * 判断响应信封是否成功（缺 code 字段按成功处理，兼容返回体为空的历史写法）。
     *
     * @param root 响应根节点
     * @return 业务码为 0 或缺省时返回 {@code true}
     */
    private boolean isSuccess(JsonNode root) {
        JsonNode code = root.path("code");
        return code.isMissingNode() || code.asInt(-1) == 0;
    }

    /**
     * 读取字符串字段（缺失/null/非文本一律返回 {@code null}）。
     *
     * @param node 目标节点
     * @return 文本值或 {@code null}
     */
    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isTextual()
                ? null : node.asText();
    }
}
