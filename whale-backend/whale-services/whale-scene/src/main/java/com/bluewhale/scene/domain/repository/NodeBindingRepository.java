package com.bluewhale.scene.domain.repository;

import com.bluewhale.scene.domain.model.NodeBinding;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 节点入口绑定仓储端口。
 *
 * @author BlueWhale
 */
public interface NodeBindingRepository {

    /**
     * 批量查询节点入口绑定。
     *
     * @param nodeIds 节点 ID 集合
     * @return 绑定列表
     */
    List<NodeBinding> listByNodeIds(Collection<Long> nodeIds);

    /**
     * 查询节点当前入口绑定。
     *
     * @param nodeId 节点 ID
     * @return 绑定
     */
    Optional<NodeBinding> findByNodeId(Long nodeId);

    /**
     * 新增绑定并回填主键。
     *
     * @param binding 绑定
     */
    void insert(NodeBinding binding);

    /**
     * 更新绑定。
     *
     * @param binding 绑定
     */
    void update(NodeBinding binding);

    /**
     * 删除节点绑定（逻辑删除）。
     *
     * @param nodeId 节点 ID
     */
    void deleteByNodeId(Long nodeId);
}
