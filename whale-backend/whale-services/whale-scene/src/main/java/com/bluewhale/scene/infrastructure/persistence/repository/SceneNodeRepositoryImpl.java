package com.bluewhale.scene.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.repository.SceneNodeRepository;
import com.bluewhale.scene.infrastructure.persistence.entity.SceneNodeEntity;
import com.bluewhale.scene.infrastructure.persistence.mapper.SceneNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 场景节点仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SceneNodeRepositoryImpl implements SceneNodeRepository {

    private final SceneNodeMapper sceneNodeMapper;

    @Override
    public List<SceneNode> listBySceneId(Long sceneId) {
        return sceneNodeMapper.selectList(new LambdaQueryWrapper<SceneNodeEntity>()
                        .eq(SceneNodeEntity::getSceneId, sceneId)
                        .orderByAsc(SceneNodeEntity::getSortNo)
                        .orderByAsc(SceneNodeEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<SceneNode> findById(Long id) {
        return Optional.ofNullable(sceneNodeMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<SceneNode> findByKey(Long sceneId, String nodeKey) {
        return Optional.ofNullable(sceneNodeMapper.selectOne(new LambdaQueryWrapper<SceneNodeEntity>()
                .eq(SceneNodeEntity::getSceneId, sceneId)
                .eq(SceneNodeEntity::getNodeKey, nodeKey))).map(this::toDomain);
    }

    @Override
    public void insert(SceneNode node) {
        SceneNodeEntity entity = new SceneNodeEntity();
        fill(entity, node);
        sceneNodeMapper.insert(entity);
        node.setId(entity.getId());
    }

    @Override
    public void update(SceneNode node) {
        SceneNodeEntity entity = new SceneNodeEntity();
        entity.setId(node.getId());
        fill(entity, node);
        sceneNodeMapper.updateById(entity);
    }

    @Override
    public void deleteById(Long id) {
        sceneNodeMapper.deleteById(id);
    }

    @Override
    public long countBySceneId(Long sceneId) {
        return sceneNodeMapper.selectCount(new LambdaQueryWrapper<SceneNodeEntity>()
                .eq(SceneNodeEntity::getSceneId, sceneId));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 领域对象
     */
    private SceneNode toDomain(SceneNodeEntity entity) {
        SceneNode node = new SceneNode();
        node.setId(entity.getId());
        node.setSceneId(entity.getSceneId());
        node.setNodeKey(entity.getNodeKey());
        node.setParentKey(entity.getParentKey());
        node.setNodeType(entity.getNodeType());
        node.setName(entity.getName());
        node.setIcon(entity.getIcon());
        node.setX(entity.getCoordinateX());
        node.setY(entity.getCoordinateY());
        node.setZ(entity.getCoordinateZ());
        node.setSortNo(entity.getSortNo());
        node.setStatus(entity.getStatus());
        return node;
    }

    /**
     * 领域对象写入实体。
     *
     * @param entity 实体
     * @param node 领域对象
     */
    private void fill(SceneNodeEntity entity, SceneNode node) {
        entity.setSceneId(node.getSceneId());
        entity.setNodeKey(node.getNodeKey());
        entity.setParentKey(node.getParentKey());
        entity.setNodeType(node.getNodeType());
        entity.setName(node.getName());
        entity.setIcon(node.getIcon());
        entity.setCoordinateX(node.getX());
        entity.setCoordinateY(node.getY());
        entity.setCoordinateZ(node.getZ());
        entity.setSortNo(node.getSortNo());
        entity.setStatus(node.getStatus());
    }
}
