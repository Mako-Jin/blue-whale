package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.music.domain.model.Tag;
import com.bluewhale.music.domain.model.TagType;
import com.bluewhale.music.domain.repository.TagRepository;
import com.bluewhale.music.infrastructure.persistence.entity.TagEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
    public Optional<Tag> findById(Long id) {
        return Optional.ofNullable(tagMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean exists(String name, TagType tagType, Long excludeId) {
        LambdaQueryWrapper<TagEntity> wrapper = new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getName, name)
                .eq(TagEntity::getTagType, tagType.name());
        if (excludeId != null) {
            wrapper.ne(TagEntity::getId, excludeId);
        }
        return tagMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(Tag tag) {
        TagEntity entity = new TagEntity();
        fill(entity, tag);
        tagMapper.insert(entity);
        tag.setId(entity.getId());
        tag.setCreateTime(entity.getCreateTime());
        tag.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Tag tag) {
        TagEntity entity = new TagEntity();
        entity.setId(tag.getId());
        fill(entity, tag);
        tagMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        tagMapper.deleteById(id);
    }

    @Override
    public List<Tag> list() {
        List<TagEntity> entities = tagMapper.selectList(new LambdaQueryWrapper<TagEntity>()
                .orderByAsc(TagEntity::getTagType)
                .orderByAsc(TagEntity::getSortNo)
                .orderByAsc(TagEntity::getId));
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Tag> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<TagEntity> entities = tagMapper.selectBatchIds(ids);
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * 将实体转换为领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Tag toDomain(TagEntity entity) {
        Tag tag = new Tag();
        tag.setId(entity.getId());
        tag.setName(entity.getName());
        tag.setTagType(parseType(entity.getTagType()));
        tag.setSortNo(entity.getSortNo());
        tag.setCreateTime(entity.getCreateTime());
        tag.setUpdateTime(entity.getUpdateTime());
        return tag;
    }

    /**
     * 将领域模型字段写入实体。
     *
     * @param entity 目标实体
     * @param tag 领域模型
     */
    private void fill(TagEntity entity, Tag tag) {
        entity.setName(tag.getName());
        entity.setTagType(tag.getTagType() == null ? TagType.GENRE.name() : tag.getTagType().name());
        entity.setSortNo(tag.getSortNo());
    }

    /**
     * 解析数据库标签类型。
     *
     * @param value 存储值
     * @return 标签类型；未知值回落 GENRE
     */
    private TagType parseType(String value) {
        for (TagType type : TagType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return TagType.GENRE;
    }
}
