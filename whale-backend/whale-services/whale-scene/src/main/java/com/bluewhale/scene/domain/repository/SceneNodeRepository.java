package com.bluewhale.scene.domain.repository;

import com.bluewhale.scene.domain.model.SceneNode;

import java.util.List;
import java.util.Optional;

/**
 * 场景节点仓储端口。
 *
 * @author BlueWhale
 */
public interface SceneNodeRepository {

    /**
     * 查询场景内全部节点（按排序号升序）。
     *
     * @param sceneId 场景 ID
     * @return 节点列表
     */
    List<SceneNode> listBySceneId(Long sceneId);

    /**
     * 按主键查询节点。
     *
     * @param id 节点主键
     * @return 节点
     */
    Optional<SceneNode> findById(Long id);

    /**
     * 按节点标识查询场景内节点。
     *
     * @param sceneId 场景 ID
     * @param nodeKey 节点标识
     * @return 节点
     */
    Optional<SceneNode> findByKey(Long sceneId, String nodeKey);

    /**
     * 新增节点并回填主键。
     *
     * @param node 节点
     */
    void insert(SceneNode node);

    /**
     * 更新节点可变字段。
     *
     * @param node 节点
     */
    void update(SceneNode node);

    /**
     * 删除节点（逻辑删除）。
     *
     * @param id 节点主键
     */
    void deleteById(Long id);

    /**
     * 统计场景节点数。
     *
     * @param sceneId 场景 ID
     * @return 节点数
     */
    long countBySceneId(Long sceneId);
}
