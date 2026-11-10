package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 告警通知接收人解析（Wave 6.10 M1）。
 *
 * <p>取值口径（不新增迁移，复用既有 {@code twin_scene.scene_config} JSON）：</p>
 * <ol>
 *   <li>场景配置 {@code alertNotify.userIds}（运营在场景里显式指定的接收人）；</li>
 *   <li>缺省回退场景创建人 {@code createdBy}——至少保证「谁建的场景谁收到」。</li>
 * </ol>
 *
 * <p>解析失败只记 WARN 并回退创建人：通知接收人算不出来时不应连累告警落库。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwinAlertRecipientResolver {

    /** JSON 解析器（sceneConfig 结构由前端约定，按需读取子节点） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** sceneConfig 中的通知配置节点 */
    private static final String NODE_ALERT_NOTIFY = "alertNotify";

    /** 通知配置中的接收人数组 */
    private static final String NODE_USER_IDS = "userIds";

    private final TwinSceneMapper sceneMapper;

    private final TwinProperties properties;

    /**
     * 解析场景的通知接收人。
     *
     * @param sceneId 场景ID
     * @return 接收人（去重、截断后；可能为空表示无人可发）
     */
    public Recipients resolve(Long sceneId) {
        TwinScene scene = sceneId == null ? null : sceneMapper.selectById(sceneId);
        if (scene == null) {
            log.warn("告警通知接收人解析失败（场景不存在）：sceneId={}", sceneId);
            return new Recipients(List.of(), null);
        }
        List<Long> configured = configuredUserIds(scene);
        List<Long> userIds = configured.isEmpty() && scene.getCreatedBy() != null
                ? List.of(scene.getCreatedBy())
                : configured;
        return new Recipients(cap(userIds), scene.getName());
    }

    /**
     * 读取 {@code sceneConfig.alertNotify.userIds}。
     *
     * @param scene 场景
     * @return 配置的接收人（解析失败返回空列表）
     */
    private List<Long> configuredUserIds(TwinScene scene) {
        String raw = scene.getSceneConfig();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JSON.readTree(raw);
            JsonNode ids = root.path(NODE_ALERT_NOTIFY).path(NODE_USER_IDS);
            if (!ids.isArray()) {
                return List.of();
            }
            List<Long> result = new ArrayList<>(ids.size());
            for (JsonNode id : ids) {
                if (id.canConvertToLong()) {
                    result.add(id.asLong());
                }
            }
            return result;
        } catch (Exception error) {
            log.warn("场景通知配置解析失败（回退创建人）：sceneId={}, reason={}",
                    scene.getId(), error.getMessage());
            return List.of();
        }
    }

    /**
     * 去重并按配置上限截断（对齐 whale-message 单次 100 人上限）。
     *
     * @param userIds 原始接收人
     * @return 处理后的接收人
     */
    private List<Long> cap(List<Long> userIds) {
        int max = Math.min(Math.max(properties.getAlertNotify().getMaxRecipients(), 1), 100);
        return userIds.stream().filter(Objects::nonNull).distinct().limit(max).toList();
    }

    /**
     * 接收人解析结果。
     *
     * @param userIds   接收人用户ID（已去重截断）
     * @param sceneName 场景名称（用于通知标题；场景不存在时为空）
     */
    public record Recipients(List<Long> userIds, String sceneName) {
    }
}
