package com.bluewhale.scene.application.support;

import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.EntryType;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.model.SceneNodeType;
import com.bluewhale.scene.domain.model.VisibilityEffect;
import com.bluewhale.scene.domain.model.VisibilitySubjectType;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 场景输入校验（应用层，纯函数，便于单元测试）。
 *
 * <p>校验分三类：字段格式（场景标识 / 节点标识 / 坐标）、树结构（重复 / 环 / 深度）、
 * 入口地址安全（协议白名单 + 内网地址拒绝 + 开放重定向防护）。所有失败统一抛
 * {@link com.bluewhale.common.core.exception.BizException}，文案来自
 * {@link SceneErrorCode}。</p>
 *
 * @author BlueWhale
 */
public final class SceneValidator {

    /** 场景标识：小写字母开头，可含小写字母 / 数字 / 连字符，长度 2–64 */
    private static final Pattern SCENE_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

    /** 节点标识：字母或数字开头，可含字母 / 数字 / 下划线 / 连字符，长度 1–64 */
    private static final Pattern NODE_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    /** 私网 172.16.0.0/12 网段 */
    private static final Pattern PRIVATE_172 = Pattern.compile("^172\\.(1[6-9]|2[0-9]|3[01])\\..*$");

    /** 2D 坐标上限（前端按 0–1000 换算百分比） */
    public static final int COORDINATE_MAX = 1000;

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private SceneValidator() {
    }

    /**
     * 校验场景标识格式。
     *
     * @param sceneKey 场景标识
     */
    public static void validateSceneKey(String sceneKey) {
        if (!StringUtils.hasText(sceneKey) || !SCENE_KEY_PATTERN.matcher(sceneKey).matches()) {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_KEY_INVALID);
        }
    }

    /**
     * 校验节点标识格式。
     *
     * @param nodeKey 节点标识
     */
    public static void validateNodeKey(String nodeKey) {
        if (!StringUtils.hasText(nodeKey) || !NODE_KEY_PATTERN.matcher(nodeKey).matches()) {
            throw SceneErrorCode.exception(SceneErrorCode.NODE_INVALID);
        }
    }

    /**
     * 校验节点类型与 2D 坐标。
     *
     * @param nodeType 节点类型
     * @param x 横坐标
     * @param y 纵坐标
     */
    public static void validateNodeShape(String nodeType, Integer x, Integer y) {
        if (!SceneNodeType.isValid(nodeType)) {
            throw SceneErrorCode.exception(SceneErrorCode.NODE_INVALID);
        }
        if (x == null || y == null || x < 0 || x > COORDINATE_MAX || y < 0 || y > COORDINATE_MAX) {
            throw SceneErrorCode.exception(SceneErrorCode.NODE_INVALID);
        }
    }

    /**
     * 校验入口类型。
     *
     * @param entryType 入口类型
     */
    public static void validateEntryType(String entryType) {
        if (!EntryType.isValid(entryType)) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_TYPE_INVALID);
        }
    }

    /**
     * 校验入口地址：仅平台内路径或 http/https 外链，禁止脚本协议、协议相对地址、
     * 内网 / 回环地址与携带用户信息的地址（开放重定向）。
     *
     * @param targetPath 入口地址
     * @param allowedSchemes 允许的外部协议（逗号分隔）
     */
    public static void validateTargetPath(String targetPath, String allowedSchemes) {
        if (!StringUtils.hasText(targetPath)) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_URL_INVALID);
        }
        String path = targetPath.trim();
        if (path.startsWith("//")) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_URL_INVALID);
        }
        if (path.startsWith("/")) {
            return;
        }
        URI uri = parse(path);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!isSchemeAllowed(scheme, allowedSchemes)) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_URL_INVALID);
        }
        if (uri.getUserInfo() != null || uri.getHost() == null || isInternalHost(uri.getHost())) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_URL_INVALID);
        }
    }

    /**
     * 校验节点树：标识重复、父节点存在、无环、深度不超限。
     *
     * @param nodes 节点列表
     * @param maxDepth 最大深度（根节点深度为 1）
     */
    public static void validateTree(List<SceneNode> nodes, int maxDepth) {
        Map<String, SceneNode> byKey = new HashMap<>();
        for (SceneNode node : nodes) {
            if (byKey.put(node.getNodeKey(), node) != null) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_INVALID);
            }
        }
        for (SceneNode node : nodes) {
            String parentKey = node.getParentKey();
            if (parentKey == null || parentKey.isBlank()) {
                continue;
            }
            if (!byKey.containsKey(parentKey)) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_INVALID);
            }
        }
        for (SceneNode node : nodes) {
            int depth = depthOf(node.getNodeKey(), byKey);
            if (depth > maxDepth) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_DEPTH_EXCEEDED);
            }
        }
    }

    /**
     * 校验可见性规则。
     *
     * @param subjectType 主体类型
     * @param subjectId 主体标识
     * @param effect 效果
     * @param nodeId 节点 ID
     * @param nodeIds 场景内节点 ID 集合
     */
    public static void validateVisibility(String subjectType, String subjectId, String effect,
                                          Long nodeId, Set<Long> nodeIds) {
        if (!VisibilitySubjectType.isValid(subjectType) || !VisibilityEffect.isValid(effect)) {
            throw SceneErrorCode.exception(SceneErrorCode.VISIBILITY_INVALID);
        }
        if (!VisibilitySubjectType.ALL.equals(subjectType) && !StringUtils.hasText(subjectId)) {
            throw SceneErrorCode.exception(SceneErrorCode.VISIBILITY_INVALID);
        }
        if (nodeId == null || nodeId < 0 || (nodeId > 0 && !nodeIds.contains(nodeId))) {
            throw SceneErrorCode.exception(SceneErrorCode.VISIBILITY_INVALID);
        }
    }

    /**
     * 计算节点深度；发现环立即抛出。
     *
     * @param nodeKey 节点标识
     * @param byKey 节点索引
     * @return 深度（根为 1）
     */
    private static int depthOf(String nodeKey, Map<String, SceneNode> byKey) {
        Set<String> visited = new HashSet<>();
        String current = nodeKey;
        int depth = 0;
        while (StringUtils.hasText(current)) {
            if (!visited.add(current)) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_CYCLE);
            }
            SceneNode node = byKey.get(current);
            if (node == null) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_INVALID);
            }
            depth++;
            if (depth > byKey.size()) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_CYCLE);
            }
            current = node.getParentKey();
        }
        return depth;
    }

    /**
     * 解析 URI。
     *
     * @param path 地址
     * @return URI
     */
    private static URI parse(String path) {
        try {
            return new URI(path);
        } catch (URISyntaxException exception) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_URL_INVALID);
        }
    }

    /**
     * 判断协议是否在白名单内。
     *
     * @param scheme 协议
     * @param allowedSchemes 逗号分隔的白名单
     * @return 允许返回 {@code true}
     */
    private static boolean isSchemeAllowed(String scheme, String allowedSchemes) {
        if (!StringUtils.hasText(allowedSchemes)) {
            return false;
        }
        Set<String> allowed = new HashSet<>();
        for (String item : allowedSchemes.split(",")) {
            allowed.add(item.trim().toLowerCase(Locale.ROOT));
        }
        return allowed.contains(scheme);
    }

    /**
     * 判断主机是否为内网 / 回环 / 链路本地地址。
     *
     * @param host 主机名
     * @return 内网返回 {@code true}
     */
    private static boolean isInternalHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        List<String> exact = new ArrayList<>(List.of(
                "localhost", "0.0.0.0", "127.0.0.1", "::1", "[::1]", "metadata.google.internal"));
        if (exact.contains(lower) || lower.endsWith(".localhost") || lower.endsWith(".internal")
                || lower.endsWith(".local")) {
            return true;
        }
        return lower.startsWith("127.") || lower.startsWith("10.") || lower.startsWith("192.168.")
                || lower.startsWith("169.254.") || PRIVATE_172.matcher(lower).matches();
    }
}
