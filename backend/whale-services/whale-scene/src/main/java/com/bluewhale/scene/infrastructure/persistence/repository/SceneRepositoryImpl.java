package com.bluewhale.scene.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneQuery;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.infrastructure.persistence.entity.SceneEntity;
import com.bluewhale.scene.infrastructure.persistence.mapper.SceneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 场景仓储实现（MyBatis-Plus）。
 *
 * <p>列表按更新时间倒序；关键字同时匹配场景标识与名称。租户条件由 starter 的
 * 租户行插件统一追加，本类不做租户判断。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SceneRepositoryImpl implements SceneRepository {

    private final SceneMapper sceneMapper;

    @Override
    public List<Scene> page(SceneQuery query) {
        Page<SceneEntity> page = sceneMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(SceneQuery query) {
        return sceneMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Scene> findById(Long id) {
        return Optional.ofNullable(sceneMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Scene> findByKey(String sceneKey) {
        return Optional.ofNullable(sceneMapper.selectOne(new LambdaQueryWrapper<SceneEntity>()
                .eq(SceneEntity::getSceneKey, sceneKey))).map(this::toDomain);
    }

    @Override
    public List<Scene> listPublished() {
        return sceneMapper.selectList(new LambdaQueryWrapper<SceneEntity>()
                        .eq(SceneEntity::getStatus, SceneStatus.PUBLISHED)
                        .orderByAsc(SceneEntity::getSceneKey))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(Scene scene) {
        SceneEntity entity = new SceneEntity();
        fill(entity, scene);
        sceneMapper.insert(entity);
        scene.setId(entity.getId());
        scene.setCreateTime(entity.getCreateTime());
        scene.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Scene scene) {
        SceneEntity entity = new SceneEntity();
        entity.setId(scene.getId());
        fill(entity, scene);
        sceneMapper.updateById(entity);
    }

    /**
     * 构建列表查询条件。
     *
     * @param query 查询条件
     * @return 条件包装器
     */
    private LambdaQueryWrapper<SceneEntity> filter(SceneQuery query) {
        LambdaQueryWrapper<SceneEntity> wrapper = new LambdaQueryWrapper<SceneEntity>()
                .eq(StringUtils.hasText(query.getStatus()), SceneEntity::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), item -> item
                        .like(SceneEntity::getSceneKey, query.getKeyword())
                        .or()
                        .like(SceneEntity::getName, query.getKeyword()))
                .orderByDesc(SceneEntity::getUpdateTime)
                .orderByDesc(SceneEntity::getId);
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 领域对象
     */
    private Scene toDomain(SceneEntity entity) {
        Scene scene = new Scene();
        scene.setId(entity.getId());
        scene.setSceneKey(entity.getSceneKey());
        scene.setName(entity.getName());
        scene.setDescription(entity.getDescription());
        scene.setStatus(entity.getStatus());
        scene.setLayoutVersion(entity.getLayoutVersion());
        scene.setPublishedAt(entity.getPublishedAt());
        scene.setCreateTime(entity.getCreateTime());
        scene.setUpdateTime(entity.getUpdateTime());
        return scene;
    }

    /**
     * 领域对象写入实体。
     *
     * @param entity 实体
     * @param scene 领域对象
     */
    private void fill(SceneEntity entity, Scene scene) {
        entity.setSceneKey(scene.getSceneKey());
        entity.setName(scene.getName());
        entity.setDescription(scene.getDescription());
        entity.setStatus(scene.getStatus());
        entity.setLayoutVersion(scene.getLayoutVersion());
        entity.setPublishedAt(scene.getPublishedAt());
    }
}
