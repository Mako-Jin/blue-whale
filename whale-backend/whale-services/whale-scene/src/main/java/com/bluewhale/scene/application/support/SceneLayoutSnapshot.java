package com.bluewhale.scene.application.support;

import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.SceneNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景布局快照生成器。
 *
 * <p>发布时把节点树与入口序列化为稳定 JSON（字段顺序固定），并计算 SHA-256 校验和；
 * 快照只含布局与入口元数据，不含用户数据，可用于回滚比对与完整性校验。</p>
 *
 * @author BlueWhale
 */
public final class SceneLayoutSnapshot {

    /** JSON 序列化器（仅用于快照，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private SceneLayoutSnapshot() {
    }

    /**
     * 生成布局快照。
     *
     * @param sceneKey 场景标识
     * @param nodes 节点列表
     * @param bindingByNodeId 节点入口索引
     * @return 快照 JSON 与校验和
     */
    public static Snapshot build(String sceneKey, List<SceneNode> nodes,
                                 Map<Long, NodeBinding> bindingByNodeId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sceneKey", sceneKey);
        root.put("nodes", nodes.stream().map(node -> nodeOf(node, bindingByNodeId.get(node.getId()))).toList());
        String json;
        try {
            json = MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("场景布局快照序列化失败", exception);
        }
        return new Snapshot(json, checksum(json));
    }

    /**
     * 计算 SHA-256 校验和。
     *
     * @param payload 内容
     * @return 十六进制校验和
     */
    public static String checksum(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 组装单个节点快照。
     *
     * @param node 节点
     * @param binding 入口绑定（可为空）
     * @return 节点快照
     */
    private static Map<String, Object> nodeOf(SceneNode node, NodeBinding binding) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nodeKey", node.getNodeKey());
        item.put("parentKey", node.getParentKey());
        item.put("nodeType", node.getNodeType());
        item.put("name", node.getName());
        item.put("icon", node.getIcon());
        item.put("x", node.getX());
        item.put("y", node.getY());
        item.put("z", node.getZ());
        item.put("sortNo", node.getSortNo());
        item.put("entryType", binding == null ? null : binding.getEntryType());
        item.put("targetPath", binding == null ? null : binding.getTargetPath());
        return item;
    }

    /**
     * 布局快照结果。
     *
     * @param layoutJson 布局 JSON
     * @param checksum SHA-256 校验和
     */
    public record Snapshot(String layoutJson, String checksum) {
    }
}
