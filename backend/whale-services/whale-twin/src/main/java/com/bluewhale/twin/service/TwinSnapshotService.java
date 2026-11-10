package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.SnapshotVO;
import com.bluewhale.twin.entity.TwinBinding;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinMarker;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.entity.TwinSnapshot;
import com.bluewhale.twin.mapper.TwinBindingMapper;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinMarkerMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.bluewhale.twin.mapper.TwinSnapshotMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 孪生发布快照服务：发布时整包冻结 + 快照查询 + 回滚重建。
 *
 * <p><b>冻结</b>：发布时把「场景元数据 + 实体树 + 绑定 + 标注」序列化为
 * 一个 JSON 整包落 {@code twin_snapshot}，版本号取发布时场景 version；
 * 同版本重新发布（发布→停用→未改动再发布）会物理移除旧行后重新落，
 * 避免撞 {@code uk_scene_version}。</p>
 *
 * <p><b>回滚</b>：已停用/草稿场景可回滚到任意历史快照——先<b>物理删除</b>
 * 当前实体/绑定/标注（对齐 whale-iot 物模型「物理替换」先例，规避逻辑删除
 * 残留占 {@code uk_scene_code}），再按快照重建，新旧实体 ID 映射回填父子
 * 与绑定/标注挂靠；场景元数据回滚（编码除外），version 自增、状态回 draft。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwinSnapshotService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** JSON 解析器（冻结与回滚共用） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TwinSnapshotMapper snapshotMapper;

    private final TwinSceneMapper sceneMapper;

    private final TwinEntityMapper entityMapper;

    private final TwinBindingMapper bindingMapper;

    private final TwinMarkerMapper markerMapper;

    private final TwinBindingService bindingService;

    private final TwinSceneService sceneService;

    /**
     * 冻结快照（由 {@link TwinSceneService#publish} 在状态切到 published 后调用）。
     *
     * @param scene  场景（已落库，version 为本次冻结版本）
     * @param remark 发布说明（可空）
     * @param userId 发布人
     * @return 快照视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public SnapshotVO freeze(TwinScene scene, String remark, Long userId) {
        snapshotMapper.physicalDeleteByVersion(scene.getId(), scene.getVersion());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scene", scenePayload(scene));
        payload.put("entities", entityPayloads(scene.getId()));
        payload.put("bindings", bindingPayloads(scene.getId()));
        payload.put("markers", markerPayloads(scene.getId()));
        TwinSnapshot snapshot = new TwinSnapshot();
        snapshot.setSceneId(scene.getId());
        snapshot.setVersion(scene.getVersion());
        snapshot.setSnapshotJson(writeJson(payload));
        snapshot.setRemark(remark);
        snapshot.setPublishedBy(userId);
        snapshotMapper.insert(snapshot);
        return toVo(snapshot);
    }

    /**
     * 场景快照列表（版本倒序）。
     *
     * @param sceneId 场景ID
     * @return 快照视图列表
     */
    public List<SnapshotVO> list(Long sceneId) {
        sceneService.requireExists(sceneId);
        List<TwinSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<TwinSnapshot>()
                .eq(TwinSnapshot::getSceneId, sceneId)
                .orderByDesc(TwinSnapshot::getVersion));
        List<SnapshotVO> result = new ArrayList<>(snapshots.size());
        for (TwinSnapshot snapshot : snapshots) {
            result.add(toVo(snapshot));
        }
        return result;
    }

    /**
     * 快照详情（含整包 JSON，回滚前预览用）。
     *
     * @param sceneId 场景ID
     * @param id      快照ID
     * @return 视图对象
     */
    public SnapshotVO get(Long sceneId, Long id) {
        TwinSnapshot snapshot = requireInScene(sceneId, id);
        SnapshotVO vo = toVo(snapshot);
        vo.setSnapshotJson(snapshot.getSnapshotJson());
        return vo;
    }

    /**
     * 回滚到指定快照（重建工作副本；场景须处于草稿/停用态）。
     *
     * @param sceneId    场景ID
     * @param snapshotId 快照ID
     * @return 被回滚到的快照视图对象
     */
    @Transactional(rollbackFor = Exception.class)
    public SnapshotVO rollback(Long sceneId, Long snapshotId) {
        TwinScene scene = sceneService.requireExists(sceneId);
        if (TwinSceneService.STATUS_PUBLISHED.equals(scene.getStatus())) {
            throw new BizException("场景已发布，请先停用后再回滚");
        }
        TwinSnapshot snapshot = requireInScene(sceneId, snapshotId);
        JsonNode root = parseJson(snapshot.getSnapshotJson());
        clearWorkingCopy(sceneId);
        restoreSceneMeta(scene, root.path("scene"));
        Map<Long, Long> idMap = restoreEntities(scene, root.path("entities"));
        restoreBindings(root.path("bindings"), idMap);
        restoreMarkers(scene, root.path("markers"), idMap);
        return toVo(snapshot);
    }

    /**
     * 物理清空场景当前工作副本（绑定 → 实体 → 标注，绑定子查询依赖实体行）。
     *
     * @param sceneId 场景ID
     */
    private void clearWorkingCopy(Long sceneId) {
        bindingMapper.physicalDeleteByScene(sceneId);
        entityMapper.physicalDeleteByScene(sceneId);
        markerMapper.physicalDeleteByScene(sceneId);
    }

    /**
     * 回滚场景元数据（编码不动，其余按快照恢复；version 自增、状态回草稿）。
     *
     * @param scene     当前场景
     * @param sceneNode 快照场景节点
     */
    private void restoreSceneMeta(TwinScene scene, JsonNode sceneNode) {
        scene.setName(textOrNull(sceneNode, "name"));
        scene.setDescription(textOrNull(sceneNode, "description"));
        scene.setThumbnailFileId(longOrNull(sceneNode, "thumbnailFileId"));
        scene.setSceneConfig(textOrNull(sceneNode, "sceneConfig"));
        scene.setVersion((scene.getVersion() == null ? 1 : scene.getVersion()) + 1);
        scene.setStatus(TwinSceneService.STATUS_DRAFT);
        sceneMapper.updateById(scene);
    }

    /**
     * 按快照重建实体树（新雪花 ID，返回旧→新 ID 映射供绑定/标注重挂靠）。
     *
     * @param scene        场景
     * @param entitiesNode 快照实体数组
     * @return 旧实体ID → 新实体ID 映射
     */
    private Map<Long, Long> restoreEntities(TwinScene scene, JsonNode entitiesNode) {
        Map<Long, Long> idMap = new HashMap<>();
        List<TwinEntity> inserted = new ArrayList<>();
        List<Long> oldParentIds = new ArrayList<>();
        for (JsonNode node : entitiesNode) {
            TwinEntity entity = new TwinEntity();
            entity.setSceneId(scene.getId());
            entity.setEntityCode(node.path("entityCode").asText());
            entity.setName(node.path("name").asText());
            entity.setModelFileId(longOrNull(node, "modelFileId"));
            entity.setTransformJson(textOrNull(node, "transformJson"));
            entity.setExtraConfig(textOrNull(node, "extraConfig"));
            entity.setSortOrder(node.path("sortOrder").asInt(0));
            entityMapper.insert(entity);
            inserted.add(entity);
            oldParentIds.add(longOrNull(node, "parentId"));
            idMap.put(node.path("id").asLong(), entity.getId());
        }
        for (int i = 0; i < inserted.size(); i++) {
            Long oldParentId = oldParentIds.get(i);
            if (oldParentId == null) {
                continue;
            }
            Long newParentId = idMap.get(oldParentId);
            if (newParentId == null) {
                // 父节点不在快照内（数据异常），按根节点兜底
                continue;
            }
            TwinEntity entity = inserted.get(i);
            entity.setParentId(newParentId);
            entityMapper.updateById(entity);
        }
        return idMap;
    }

    /**
     * 按快照重建绑定（entityId 按新旧映射换算，实体缺失的绑定丢弃）。
     *
     * @param bindingsNode 快照绑定数组
     * @param idMap        旧实体ID → 新实体ID 映射
     */
    private void restoreBindings(JsonNode bindingsNode, Map<Long, Long> idMap) {
        for (JsonNode node : bindingsNode) {
            Long newEntityId = remapEntity(node, idMap);
            if (newEntityId == null) {
                continue;
            }
            TwinBinding binding = new TwinBinding();
            binding.setEntityId(newEntityId);
            binding.setSourceType(node.path("sourceType").asText());
            binding.setBindKey(node.path("bindKey").asText());
            binding.setPropertyMap(textOrNull(node, "propertyMap"));
            binding.setPollIntervalMs(node.path("pollIntervalMs").asInt(5000));
            binding.setEnabled(node.path("enabled").asInt(1));
            bindingMapper.insert(binding);
        }
    }

    /**
     * 按快照重建标注（挂靠实体按新旧映射换算，实体缺失则退化为场景级锚点）。
     *
     * @param scene       场景
     * @param markersNode 快照标注数组
     * @param idMap       旧实体ID → 新实体ID 映射
     */
    private void restoreMarkers(TwinScene scene, JsonNode markersNode, Map<Long, Long> idMap) {
        for (JsonNode node : markersNode) {
            TwinMarker marker = new TwinMarker();
            marker.setSceneId(scene.getId());
            marker.setEntityId(remapEntity(node, idMap));
            marker.setMarkerType(node.path("markerType").asText("label"));
            marker.setContent(textOrNull(node, "content"));
            marker.setPositionJson(textOrNull(node, "positionJson"));
            marker.setLinkUrl(textOrNull(node, "linkUrl"));
            marker.setEnabled(node.path("enabled").asInt(1));
            markerMapper.insert(marker);
        }
    }

    /**
     * 快照节点的 entityId 按新旧映射换算（缺失/无法映射视为场景级）。
     *
     * @param node  快照节点
     * @param idMap 旧实体ID → 新实体ID 映射
     * @return 新实体ID；无法映射返回 {@code null}
     */
    private Long remapEntity(JsonNode node, Map<Long, Long> idMap) {
        JsonNode entityIdNode = node.path("entityId");
        if (entityIdNode.isMissingNode() || entityIdNode.isNull() || entityIdNode.asLong() <= 0) {
            return null;
        }
        Long newId = idMap.get(entityIdNode.asLong());
        if (newId == null) {
            log.warn("快照回滚：实体 {} 不在快照实体集内，挂靠关系退化为场景级", entityIdNode.asLong());
        }
        return newId;
    }

    /**
     * 构造快照场景节点（编码入包仅作追溯，回滚不改编码）。
     *
     * @param scene 场景
     * @return 节点
     */
    private static Map<String, Object> scenePayload(TwinScene scene) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", scene.getId());
        node.put("code", scene.getCode());
        node.put("name", scene.getName());
        node.put("description", scene.getDescription());
        node.put("thumbnailFileId", scene.getThumbnailFileId());
        node.put("sceneConfig", scene.getSceneConfig());
        node.put("version", scene.getVersion());
        return node;
    }

    /**
     * 场景实体整包（父链原样，回滚时重映射）。
     *
     * @param sceneId 场景ID
     * @return 节点列表
     */
    private List<Map<String, Object>> entityPayloads(Long sceneId) {
        List<TwinEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<TwinEntity>()
                .eq(TwinEntity::getSceneId, sceneId)
                .orderByAsc(TwinEntity::getSortOrder)
                .orderByAsc(TwinEntity::getId));
        List<Map<String, Object>> nodes = new ArrayList<>(entities.size());
        for (TwinEntity entity : entities) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", entity.getId());
            node.put("entityCode", entity.getEntityCode());
            node.put("name", entity.getName());
            node.put("parentId", entity.getParentId());
            node.put("modelFileId", entity.getModelFileId());
            node.put("transformJson", entity.getTransformJson());
            node.put("extraConfig", entity.getExtraConfig());
            node.put("sortOrder", entity.getSortOrder());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 场景绑定整包。
     *
     * @param sceneId 场景ID
     * @return 节点列表
     */
    private List<Map<String, Object>> bindingPayloads(Long sceneId) {
        List<TwinBinding> bindings = bindingService.listByScene(sceneId);
        List<Map<String, Object>> nodes = new ArrayList<>(bindings.size());
        for (TwinBinding binding : bindings) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", binding.getId());
            node.put("entityId", binding.getEntityId());
            node.put("sourceType", binding.getSourceType());
            node.put("bindKey", binding.getBindKey());
            node.put("propertyMap", binding.getPropertyMap());
            node.put("pollIntervalMs", binding.getPollIntervalMs());
            node.put("enabled", binding.getEnabled());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 场景标注整包。
     *
     * @param sceneId 场景ID
     * @return 节点列表
     */
    private List<Map<String, Object>> markerPayloads(Long sceneId) {
        List<TwinMarker> markers = markerMapper.selectList(new LambdaQueryWrapper<TwinMarker>()
                .eq(TwinMarker::getSceneId, sceneId)
                .orderByAsc(TwinMarker::getId));
        List<Map<String, Object>> nodes = new ArrayList<>(markers.size());
        for (TwinMarker marker : markers) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", marker.getId());
            node.put("entityId", marker.getEntityId());
            node.put("markerType", marker.getMarkerType());
            node.put("content", marker.getContent());
            node.put("positionJson", marker.getPositionJson());
            node.put("linkUrl", marker.getLinkUrl());
            node.put("enabled", marker.getEnabled());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 校验快照属于指定场景。
     *
     * @param sceneId 场景ID
     * @param id      快照ID
     * @return 快照实体
     */
    private TwinSnapshot requireInScene(Long sceneId, Long id) {
        TwinSnapshot snapshot = snapshotMapper.selectById(id);
        if (snapshot == null || !sceneId.equals(snapshot.getSceneId())) {
            throw new BizException("快照不存在或不属于当前场景");
        }
        return snapshot;
    }

    /**
     * 序列化快照整包（失败视为服务端错误）。
     *
     * @param payload 整包
     * @return JSON 字符串
     */
    private static String writeJson(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BizException("快照序列化失败：" + e.getMessage());
        }
    }

    /**
     * 解析快照整包。
     *
     * @param json JSON 字符串
     * @return 根节点
     */
    private static JsonNode parseJson(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new BizException("快照内容损坏，无法解析");
        }
    }

    /**
     * 取节点文本字段（缺失/null 返回 {@code null}）。
     *
     * @param node 节点
     * @param name 字段名
     * @return 文本值
     */
    private static String textOrNull(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * 取节点整数字段（缺失/null/0 返回 {@code null}，0 视为未设置）。
     *
     * @param node 节点
     * @param name 字段名
     * @return 整数值
     */
    private static Long longOrNull(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull() || value.asLong() <= 0) {
            return null;
        }
        return value.asLong();
    }

    /**
     * 快照转视图对象（不携带整包 JSON；详情由 {@link #get} 单独补充）。
     *
     * @param snapshot 快照实体
     * @return 视图对象
     */
    private static SnapshotVO toVo(TwinSnapshot snapshot) {
        return SnapshotVO.builder()
                .id(String.valueOf(snapshot.getId()))
                .sceneId(String.valueOf(snapshot.getSceneId()))
                .version(snapshot.getVersion())
                .remark(snapshot.getRemark())
                .publishedBy(snapshot.getPublishedBy() == null
                        ? null : String.valueOf(snapshot.getPublishedBy()))
                .publishTime(snapshot.getPublishTime() == null
                        ? null : TIME_FORMAT.format(snapshot.getPublishTime()))
                .build();
    }
}
