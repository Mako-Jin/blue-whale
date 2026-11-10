package com.bluewhale.scene.domain.repository;

import com.bluewhale.scene.domain.model.SceneVisibility;

import java.util.List;

/**
 * 场景可见性仓储端口。
 *
 * @author BlueWhale
 */
public interface SceneVisibilityRepository {

    /**
     * 查询场景全部可见性规则（含节点级与场景级）。
     *
     * @param sceneId 场景 ID
     * @return 规则列表
     */
    List<SceneVisibility> listBySceneId(Long sceneId);

    /**
     * 清空场景可见性规则（逻辑删除）。
     *
     * @param sceneId 场景 ID
     */
    void deleteBySceneId(Long sceneId);

    /**
     * 删除指定节点的可见性规则（逻辑删除）。
     *
     * @param nodeId 节点 ID
     */
    void deleteByNodeId(Long nodeId);

    /**
     * 新增可见性规则。
     *
     * @param visibility 规则
     */
    void insert(SceneVisibility visibility);
}
