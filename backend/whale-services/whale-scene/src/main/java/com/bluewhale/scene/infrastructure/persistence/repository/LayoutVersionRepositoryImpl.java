package com.bluewhale.scene.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.scene.domain.model.LayoutVersion;
import com.bluewhale.scene.domain.repository.LayoutVersionRepository;
import com.bluewhale.scene.infrastructure.persistence.entity.LayoutVersionEntity;
import com.bluewhale.scene.infrastructure.persistence.mapper.LayoutVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 布局版本快照仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LayoutVersionRepositoryImpl implements LayoutVersionRepository {

    private final LayoutVersionMapper layoutVersionMapper;

    @Override
    public Optional<LayoutVersion> findLatest(Long sceneId) {
        return Optional.ofNullable(layoutVersionMapper.selectOne(new LambdaQueryWrapper<LayoutVersionEntity>()
                .eq(LayoutVersionEntity::getSceneId, sceneId)
                .orderByDesc(LayoutVersionEntity::getVersion)
                .last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public void insert(LayoutVersion layoutVersion) {
        LayoutVersionEntity entity = new LayoutVersionEntity();
        entity.setSceneId(layoutVersion.getSceneId());
        entity.setVersion(layoutVersion.getVersion());
        entity.setLayoutJson(layoutVersion.getLayoutJson());
        entity.setChecksum(layoutVersion.getChecksum());
        layoutVersionMapper.insert(entity);
        layoutVersion.setId(entity.getId());
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 领域对象
     */
    private LayoutVersion toDomain(LayoutVersionEntity entity) {
        LayoutVersion layoutVersion = new LayoutVersion();
        layoutVersion.setId(entity.getId());
        layoutVersion.setSceneId(entity.getSceneId());
        layoutVersion.setVersion(entity.getVersion());
        layoutVersion.setLayoutJson(entity.getLayoutJson());
        layoutVersion.setChecksum(entity.getChecksum());
        layoutVersion.setCreateTime(entity.getCreateTime());
        return layoutVersion;
    }
}
