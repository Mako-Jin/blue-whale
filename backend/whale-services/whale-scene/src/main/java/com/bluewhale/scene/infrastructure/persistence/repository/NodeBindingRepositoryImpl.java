package com.bluewhale.scene.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.repository.NodeBindingRepository;
import com.bluewhale.scene.infrastructure.persistence.entity.NodeBindingEntity;
import com.bluewhale.scene.infrastructure.persistence.mapper.NodeBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 节点入口绑定仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class NodeBindingRepositoryImpl implements NodeBindingRepository {

    private final NodeBindingMapper nodeBindingMapper;

    @Override
    public List<NodeBinding> listByNodeIds(Collection<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        return nodeBindingMapper.selectList(new LambdaQueryWrapper<NodeBindingEntity>()
                        .in(NodeBindingEntity::getNodeId, nodeIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<NodeBinding> findByNodeId(Long nodeId) {
        return Optional.ofNullable(nodeBindingMapper.selectOne(new LambdaQueryWrapper<NodeBindingEntity>()
                .eq(NodeBindingEntity::getNodeId, nodeId))).map(this::toDomain);
    }

    @Override
    public void insert(NodeBinding binding) {
        NodeBindingEntity entity = new NodeBindingEntity();
        fill(entity, binding);
        nodeBindingMapper.insert(entity);
        binding.setId(entity.getId());
    }

    @Override
    public void update(NodeBinding binding) {
        NodeBindingEntity entity = new NodeBindingEntity();
        entity.setId(binding.getId());
        fill(entity, binding);
        nodeBindingMapper.updateById(entity);
    }

    @Override
    public void deleteByNodeId(Long nodeId) {
        nodeBindingMapper.delete(new LambdaQueryWrapper<NodeBindingEntity>()
                .eq(NodeBindingEntity::getNodeId, nodeId));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 领域对象
     */
    private NodeBinding toDomain(NodeBindingEntity entity) {
        NodeBinding binding = new NodeBinding();
        binding.setId(entity.getId());
        binding.setNodeId(entity.getNodeId());
        binding.setEntryType(entity.getEntryType());
        binding.setTargetPath(entity.getTargetPath());
        binding.setPermissionCode(entity.getPermissionCode());
        binding.setEnabled(entity.getEnabled() == null || entity.getEnabled() == 1);
        return binding;
    }

    /**
     * 领域对象写入实体。
     *
     * @param entity 实体
     * @param binding 领域对象
     */
    private void fill(NodeBindingEntity entity, NodeBinding binding) {
        entity.setNodeId(binding.getNodeId());
        entity.setEntryType(binding.getEntryType());
        entity.setTargetPath(binding.getTargetPath());
        entity.setPermissionCode(binding.getPermissionCode());
        entity.setEnabled(Boolean.FALSE.equals(binding.getEnabled()) ? 0 : 1);
    }
}
