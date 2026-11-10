package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.video.domain.model.Creator;
import com.bluewhale.video.domain.model.CreatorQuery;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.infrastructure.persistence.entity.CreatorEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.CreatorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 创作者仓储实现（每用户一条，MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CreatorRepositoryImpl implements CreatorRepository {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    private final CreatorMapper creatorMapper;

    @Override
    public Optional<Creator> findByUserId(Long userId) {
        return Optional.ofNullable(creatorMapper.selectOne(new LambdaQueryWrapper<CreatorEntity>()
                .eq(CreatorEntity::getUserId, userId)
                .orderByDesc(CreatorEntity::getId)
                .last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public Optional<Creator> findById(Long id) {
        return Optional.ofNullable(creatorMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(Creator creator) {
        CreatorEntity entity = new CreatorEntity();
        fill(entity, creator);
        creatorMapper.insert(entity);
        creator.setId(entity.getId());
    }

    @Override
    public void update(Creator creator) {
        creatorMapper.update(null, new LambdaUpdateWrapper<CreatorEntity>()
                .eq(CreatorEntity::getId, creator.getId())
                .set(CreatorEntity::getDisplayName, creator.getDisplayName())
                .set(CreatorEntity::getBio, creator.getBio())
                .set(CreatorEntity::getAvatarFileId, creator.getAvatarFileId())
                .set(CreatorEntity::getStatus,
                        creator.getStatus() == null ? STATUS_ENABLED : creator.getStatus()));
    }

    @Override
    public List<Creator> page(CreatorQuery query) {
        Page<CreatorEntity> page = creatorMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)),
                filter(query).orderByDesc(CreatorEntity::getId));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CreatorQuery query) {
        return creatorMapper.selectCount(filter(query));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<CreatorEntity> filter(CreatorQuery query) {
        return new LambdaQueryWrapper<CreatorEntity>()
                .like(StringUtils.hasText(query.getKeyword()), CreatorEntity::getDisplayName, query.getKeyword())
                .eq(query.getStatus() != null, CreatorEntity::getStatus, query.getStatus());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 创作者
     */
    private Creator toDomain(CreatorEntity entity) {
        Creator creator = new Creator();
        creator.setId(entity.getId());
        creator.setUserId(entity.getUserId());
        creator.setDisplayName(entity.getDisplayName());
        creator.setAvatarFileId(entity.getAvatarFileId());
        creator.setBio(entity.getBio());
        creator.setStatus(entity.getStatus());
        return creator;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 目标实体
     * @param creator 创作者
     */
    private void fill(CreatorEntity entity, Creator creator) {
        entity.setId(creator.getId());
        entity.setUserId(creator.getUserId());
        entity.setDisplayName(creator.getDisplayName());
        entity.setAvatarFileId(creator.getAvatarFileId());
        entity.setBio(creator.getBio());
        entity.setStatus(creator.getStatus() == null ? STATUS_ENABLED : creator.getStatus());
    }
}
