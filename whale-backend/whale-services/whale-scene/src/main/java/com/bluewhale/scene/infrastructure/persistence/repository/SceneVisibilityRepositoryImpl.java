package com.bluewhale.scene.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.scene.domain.model.SceneVisibility;
import com.bluewhale.scene.domain.repository.SceneVisibilityRepository;
import com.bluewhale.scene.infrastructure.persistence.entity.SceneVisibilityEntity;
import com.bluewhale.scene.infrastructure.persistence.mapper.SceneVisibilityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 场景可见性仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SceneVisibilityRepositoryImpl implements SceneVisibilityRepository {

    private final SceneVisibilityMapper sceneVisibilityMapper;

    @Override
    public List<SceneVisibility> listBySceneId(Long sceneId) {
        return sceneVisibilityMapper.selectList(new LambdaQueryWrapper<SceneVisibilityEntity>()
                        .eq(SceneVisibilityEntity::getSceneId, sceneId)
                        .orderByAsc(SceneVisibilityEntity::getNodeId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteBySceneId(Long sceneId) {
        sceneVisibilityMapper.delete(new LambdaQueryWrapper<SceneVisibilityEntity>()
                .eq(SceneVisibilityEntity::getSceneId, sceneId));
    }

    @Override
    public void deleteByNodeId(Long nodeId) {
        sceneVisibilityMapper.delete(new LambdaQueryWrapper<SceneVisibilityEntity>()
                .eq(SceneVisibilityEntity::getNodeId, nodeId));
    }

    @Override
    public void insert(SceneVisibility visibility) {
        SceneVisibilityEntity entity = new SceneVisibilityEntity();
        entity.setSceneId(visibility.getSceneId());
        entity.setNodeId(visibility.getNodeId());
        entity.setSubjectType(visibility.getSubjectType());
        entity.setSubjectId(visibility.getSubjectId());
        entity.setEffect(visibility.getEffect());
        sceneVisibilityMapper.insert(entity);
        visibility.setId(entity.getId());
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 领域对象
     */
    private SceneVisibility toDomain(SceneVisibilityEntity entity) {
        SceneVisibility visibility = new SceneVisibility();
        visibility.setId(entity.getId());
        visibility.setSceneId(entity.getSceneId());
        visibility.setNodeId(entity.getNodeId());
        visibility.setSubjectType(entity.getSubjectType());
        visibility.setSubjectId(entity.getSubjectId());
        visibility.setEffect(entity.getEffect());
        return visibility;
    }
}
