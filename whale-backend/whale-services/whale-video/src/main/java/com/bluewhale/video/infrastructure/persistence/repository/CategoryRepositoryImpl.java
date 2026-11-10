package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.video.domain.model.Category;
import com.bluewhale.video.domain.repository.CategoryRepository;
import com.bluewhale.video.infrastructure.persistence.entity.CategoryEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分类仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> listEnabled() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                        .eq(CategoryEntity::getStatus, STATUS_ENABLED)
                        .orderByAsc(CategoryEntity::getSortNo)
                        .orderByAsc(CategoryEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(Category category) {
        CategoryEntity entity = new CategoryEntity();
        fill(entity, category);
        categoryMapper.insert(entity);
        category.setId(entity.getId());
    }

    @Override
    public void update(Category category) {
        CategoryEntity entity = new CategoryEntity();
        fill(entity, category);
        categoryMapper.updateById(entity);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 分类
     */
    private Category toDomain(CategoryEntity entity) {
        Category category = new Category();
        category.setId(entity.getId());
        category.setParentId(entity.getParentId() == null ? 0L : entity.getParentId());
        category.setName(entity.getName());
        category.setCode(entity.getCode());
        category.setContentTypeScope(entity.getContentTypeScope());
        category.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        category.setStatus(entity.getStatus());
        category.setCreateTime(entity.getCreateTime());
        return category;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 目标实体
     * @param category 分类
     */
    private void fill(CategoryEntity entity, Category category) {
        entity.setId(category.getId());
        entity.setParentId(category.getParentId() == null ? 0L : category.getParentId());
        entity.setName(category.getName());
        entity.setCode(category.getCode());
        entity.setContentTypeScope(category.getContentTypeScope());
        entity.setSortNo(category.getSortNo() == null ? 0 : category.getSortNo());
        entity.setStatus(category.getStatus() == null ? STATUS_ENABLED : category.getStatus());
    }
}
