package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.TwinAlertNotifySettingsRequest;
import com.bluewhale.twin.dto.TwinAlertNotifySettingsVO;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 场景级告警通知设置服务（Wave 6.10 M2）。
 *
 * <p>设置落在既有 {@code twin_scene.scene_config} 的 {@code alertNotify} 节点下，
 * 不新增迁移：{@code {webhookEnabled, inappEnabled, minLevel, userIds}}。
 * 写回只改 {@code scene_config}，<b>不 bump version、不受「已发布禁止修改」限制</b>——
 * 通知设置是运营配置，既不影响几何/绑定，也不参与发布快照语义。</p>
 *
 * <p>叠加规则（单向收紧）：场景可以把某个通道关掉、把门槛抬到 critical，
 * 但不能突破全局开关与全局门槛；全局关掉时场景设什么都发不出去。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwinAlertNotifySettingsService {

    /** JSON 解析器（与双端既有 sceneConfig 读写口径一致） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** sceneConfig 中的通知配置节点 */
    private static final String NODE_ALERT_NOTIFY = "alertNotify";

    /** 节点：webhook 开关 */
    private static final String NODE_WEBHOOK_ENABLED = "webhookEnabled";

    /** 节点：站内信开关 */
    private static final String NODE_INAPP_ENABLED = "inappEnabled";

    /** 节点：最低级别 */
    private static final String NODE_MIN_LEVEL = "minLevel";

    /** 节点：接收人 */
    private static final String NODE_USER_IDS = "userIds";

    /** sceneConfig 列宽上限（与 SceneSaveRequest 校验一致） */
    private static final int CONFIG_MAX_LENGTH = 16384;

    /** 接收人上限（与通知配置 maxRecipients 口径一致） */
    private static final int USER_IDS_MAX = 20;

    /** 级别权重：{@code normal} 不参与外发 */
    private static final Map<String, Integer> LEVEL_RANK = Map.of("warn", 1, "critical", 2);

    /** 缺省级别（非法/缺失时按 warn 处理） */
    private static final String DEFAULT_LEVEL = "warn";

    private final TwinSceneMapper sceneMapper;

    private final TwinProperties properties;

    /**
     * 读取场景设置（含叠加后的生效值）。
     *
     * @param sceneId 场景ID
     * @return 设置视图
     */
    public TwinAlertNotifySettingsVO get(Long sceneId) {
        TwinScene scene = requireScene(sceneId);
        return buildVo(sceneId, read(scene));
    }

    /**
     * 保存场景设置（整体覆盖，{@code null} 表示清除该项回到「跟随全局」）。
     *
     * @param sceneId 场景ID
     * @param request 设置请求
     * @return 保存后的设置视图
     */
    public TwinAlertNotifySettingsVO save(Long sceneId, TwinAlertNotifySettingsRequest request) {
        TwinScene scene = requireScene(sceneId);
        String json = mergeConfig(scene.getSceneConfig(), request);
        // 刻意不 bump version、不校验发布状态：通知设置属运营配置，编辑锁只管几何/绑定
        sceneMapper.update(null, new LambdaUpdateWrapper<TwinScene>()
                .eq(TwinScene::getId, sceneId)
                .set(TwinScene::getSceneConfig, json));
        log.info("场景通知设置已更新：sceneId={}, webhook={}, inapp={}, minLevel={}, recipients={}",
                sceneId, request.getWebhookEnabled(), request.getInappEnabled(),
                request.getMinLevel(), request.getUserIds() == null ? 0 : request.getUserIds().size());
        return buildVo(sceneId, read(json));
    }

    /**
     * 把设置合并进 sceneConfig 原文（保留其它键；全部清除且无其它键时返回 {@code null}）。
     *
     * @param raw     原 sceneConfig（可空）
     * @param request 设置请求
     * @return 合并后的 sceneConfig；空对象时返回 {@code null}
     */
    String mergeConfig(String raw, TwinAlertNotifySettingsRequest request) {
        ObjectNode root = parseRoot(raw);
        ObjectNode notify = root.path(NODE_ALERT_NOTIFY).isObject()
                ? (ObjectNode) root.path(NODE_ALERT_NOTIFY)
                : JSON.createObjectNode();
        putOrRemoveBoolean(notify, NODE_WEBHOOK_ENABLED, request.getWebhookEnabled());
        putOrRemoveBoolean(notify, NODE_INAPP_ENABLED, request.getInappEnabled());
        putOrRemoveText(notify, NODE_MIN_LEVEL, request.getMinLevel());
        putOrRemoveUserIds(notify, request.getUserIds());
        if (notify.isEmpty()) {
            root.remove(NODE_ALERT_NOTIFY);
        } else {
            root.set(NODE_ALERT_NOTIFY, notify);
        }
        String json = root.isEmpty() ? null : root.toString();
        if (json != null && json.length() > CONFIG_MAX_LENGTH) {
            throw new BizException("场景配置超出长度上限（" + CONFIG_MAX_LENGTH + " 字符）");
        }
        return json;
    }

    /**
     * 读取场景的原始通知设置（运行时外发用，不构造视图）。
     *
     * @param sceneId 场景ID（可空）
     * @return 场景设置；场景不存在时返回全空的设置（等价「跟随全局」）
     */
    public SceneSettings current(Long sceneId) {
        if (sceneId == null) {
            return SceneSettings.EMPTY;
        }
        TwinScene scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            return SceneSettings.EMPTY;
        }
        return read(scene);
    }

    /**
     * 场景是否允许外发 webhook（场景显式关闭即不允许）。
     *
     * @param settings 场景设置
     * @return 允许返回 {@code true}
     */
    public boolean webhookAllowed(SceneSettings settings) {
        return !Boolean.FALSE.equals(settings.webhookEnabled());
    }

    /**
     * 场景是否允许外发站内信（场景显式关闭即不允许）。
     *
     * @param settings 场景设置
     * @return 允许返回 {@code true}
     */
    public boolean inappAllowed(SceneSettings settings) {
        return !Boolean.FALSE.equals(settings.inappEnabled());
    }

    /**
     * 告警级别是否达到「全局 + 场景」共同门槛（取更严者）。
     *
     * @param level    告警级别
     * @param settings 场景设置
     * @return 达到门槛返回 {@code true}
     */
    public boolean reachesMinLevel(String level, SceneSettings settings) {
        int rank = rank(level);
        return rank > 0 && rank >= rank(effectiveMinLevel(settings));
    }

    /**
     * 实际生效的最低级别（全局与场景取更严者）。
     *
     * @param settings 场景设置
     * @return 生效级别
     */
    public String effectiveMinLevel(SceneSettings settings) {
        String global = normalize(properties.getAlertNotify().getMinLevel());
        String scene = normalize(settings.minLevel());
        return rank(scene) > rank(global) ? scene : global;
    }

    // ---- 内部：JSON 读写 ----

    /**
     * 从场景行解析设置。
     *
     * @param scene 场景
     * @return 设置
     */
    private SceneSettings read(TwinScene scene) {
        return read(scene == null ? null : scene.getSceneConfig());
    }

    /**
     * 从 sceneConfig 原文解析设置（解析失败按全空处理，等价跟随全局）。
     *
     * @param raw sceneConfig 原文
     * @return 设置
     */
    private SceneSettings read(String raw) {
        if (!StringUtils.hasText(raw)) {
            return SceneSettings.EMPTY;
        }
        try {
            JsonNode notify = parseRoot(raw).path(NODE_ALERT_NOTIFY);
            if (!notify.isObject()) {
                return SceneSettings.EMPTY;
            }
            Boolean webhook = asBoolean(notify.path(NODE_WEBHOOK_ENABLED));
            Boolean inapp = asBoolean(notify.path(NODE_INAPP_ENABLED));
            String minLevel = notify.path(NODE_MIN_LEVEL).isTextual()
                    ? normalize(notify.path(NODE_MIN_LEVEL).asText()) : null;
            List<Long> userIds = new ArrayList<>();
            JsonNode ids = notify.path(NODE_USER_IDS);
            if (ids.isArray()) {
                for (JsonNode id : ids) {
                    if (id.canConvertToLong()) {
                        userIds.add(id.asLong());
                    }
                }
            }
            return new SceneSettings(webhook, inapp, minLevel, List.copyOf(userIds));
        } catch (Exception error) {
            log.warn("场景通知设置解析失败（按跟随全局处理）：reason={}", error.getMessage());
            return SceneSettings.EMPTY;
        }
    }

    /**
     * 解析 sceneConfig 为对象节点（空/非法时返回空对象）。
     *
     * @param raw sceneConfig 原文
     * @return 对象节点
     */
    private ObjectNode parseRoot(String raw) {
        if (!StringUtils.hasText(raw)) {
            return JSON.createObjectNode();
        }
        try {
            JsonNode node = JSON.readTree(raw);
            return node instanceof ObjectNode objectNode ? objectNode : JSON.createObjectNode();
        } catch (Exception error) {
            log.warn("sceneConfig 解析失败（按空配置覆盖）：reason={}", error.getMessage());
            return JSON.createObjectNode();
        }
    }

    /**
     * 写入布尔项（{@code null} 表示删除该键）。
     *
     * @param node  目标节点
     * @param field 字段名
     * @param value 值
     */
    private void putOrRemoveBoolean(ObjectNode node, String field, Boolean value) {
        if (value == null) {
            node.remove(field);
        } else {
            node.put(field, value);
        }
    }

    /**
     * 写入文本项（空表示删除该键）。
     *
     * @param node  目标节点
     * @param field 字段名
     * @param value 值
     */
    private void putOrRemoveText(ObjectNode node, String field, String value) {
        if (!StringUtils.hasText(value)) {
            node.remove(field);
            return;
        }
        node.put(field, normalize(value));
    }

    /**
     * 写入接收人（空表示删除该键）。
     *
     * @param node    目标节点
     * @param userIds 接收人
     */
    private void putOrRemoveUserIds(ObjectNode node, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            node.remove(NODE_USER_IDS);
            return;
        }
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct()
                .limit(USER_IDS_MAX).toList();
        if (distinct.isEmpty()) {
            node.remove(NODE_USER_IDS);
            return;
        }
        ArrayNode array = JSON.createArrayNode();
        distinct.forEach(array::add);
        node.set(NODE_USER_IDS, array);
    }

    /**
     * 组装设置视图（叠加全局配置，给出实际生效值）。
     *
     * @param sceneId  场景ID
     * @param settings 场景设置
     * @return 视图
     */
    private TwinAlertNotifySettingsVO buildVo(Long sceneId, SceneSettings settings) {
        TwinProperties.AlertNotify global = properties.getAlertNotify();
        boolean globalWebhookReady = global.isEnabled()
                && global.getWebhookUrls() != null && !global.getWebhookUrls().isEmpty();
        String globalMinLevel = normalize(global.getMinLevel());
        List<String> userIds = settings.userIds().stream().map(String::valueOf).toList();
        return TwinAlertNotifySettingsVO.builder()
                .sceneId(sceneId == null ? null : sceneId.toString())
                .webhookEnabled(settings.webhookEnabled())
                .inappEnabled(settings.inappEnabled())
                .minLevel(settings.minLevel())
                .userIds(userIds)
                .fallbackToCreator(userIds.isEmpty())
                .effectiveWebhookEnabled(globalWebhookReady && webhookAllowed(settings))
                .effectiveInappEnabled(global.isInappEnabled() && inappAllowed(settings))
                .effectiveMinLevel(effectiveMinLevel(settings))
                .globalMinLevel(globalMinLevel)
                .globalWebhookReady(globalWebhookReady)
                .globalInappEnabled(global.isInappEnabled())
                .build();
    }

    /**
     * 读取布尔节点（非布尔返回 {@code null}，表示未设置）。
     *
     * @param node 节点
     * @return 布尔值或 null
     */
    private Boolean asBoolean(JsonNode node) {
        return node.isBoolean() ? node.asBoolean() : null;
    }

    /**
     * 级别归一（非法值回落到缺省 warn）。
     *
     * @param level 级别
     * @return 归一后的级别
     */
    private String normalize(String level) {
        if (!StringUtils.hasText(level)) {
            return DEFAULT_LEVEL;
        }
        String normalized = level.trim().toLowerCase();
        return LEVEL_RANK.containsKey(normalized) ? normalized : DEFAULT_LEVEL;
    }

    /**
     * 级别权重（{@code normal} 与未知级别为 0）。
     *
     * @param level 级别
     * @return 权重
     */
    private int rank(String level) {
        return LEVEL_RANK.getOrDefault(level == null ? "" : level.trim().toLowerCase(), 0);
    }

    /**
     * 取场景，不存在即抛业务异常。
     *
     * @param sceneId 场景ID
     * @return 场景
     */
    private TwinScene requireScene(Long sceneId) {
        TwinScene scene = sceneId == null ? null : sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new BizException("场景不存在");
        }
        return scene;
    }

    /**
     * 场景原始通知设置。
     *
     * @param webhookEnabled 场景级 webhook 开关（null = 未设置）
     * @param inappEnabled   场景级站内信开关（null = 未设置）
     * @param minLevel       场景级最低级别（null = 未设置）
     * @param userIds        场景级接收人（空 = 回退创建人）
     */
    public record SceneSettings(Boolean webhookEnabled, Boolean inappEnabled,
                                String minLevel, List<Long> userIds) {

        /** 全空设置：等价「跟随全局」 */
        public static final SceneSettings EMPTY = new SceneSettings(null, null, null, List.of());
    }
}
