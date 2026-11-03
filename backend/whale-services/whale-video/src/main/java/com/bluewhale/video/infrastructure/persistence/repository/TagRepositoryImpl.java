package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.video.domain.model.Tag;
import com.bluewhale.video.domain.model.TagType;
import com.bluewhale.video.domain.repository.TagRepository;
import com.bluewhale.video.infrastructure.persistence.entity.TagEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 标签仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;

    @Override
    public List<Tag> listAll() {
        return tagMapper.selectList(new LambdaQueryWrapper<TagEntity>()
                        .orderByAsc(TagEntity::getSortNo)
                        .orderByAsc(TagEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Tag> findById(Long id) {
        return Optional.ofNullable(tagMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Tag> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return tagMapper.selectBatchIds(ids).stream().map(this::toDomain).toList();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 标签
     */
    private Tag toDomain(TagEntity entity) {
        Tag tag = new Tag();
        tag.setId(entity.getId());
        tag.setName(entity.getName());
        tag.setTagType(TagType.parse(entity.getTagType()));
        tag.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        return tag;
    }
}
