package com.bluewhale.mall.catalog.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mall.catalog.domain.model.Category;
import com.bluewhale.mall.catalog.domain.model.CategoryQuery;
import com.bluewhale.mall.catalog.domain.model.CategoryStatus;
import com.bluewhale.mall.catalog.domain.repository.CategoryRepository;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.CategoryEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 类目仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> list(CategoryQuery query) {
        return categoryMapper.selectList(filter(query)).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Category> listChildren(Long parentId) {
        long parent = parentId == null ? Category.ROOT_PARENT_ID : parentId;
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                        .eq(CategoryEntity::getParentId, parent)
                        .orderByAsc(CategoryEntity::getSortNo)
                        .orderByAsc(CategoryEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsSiblingName(Long parentId, String categoryName, Long excludeId) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getParentId, parentId == null ? Category.ROOT_PARENT_ID : parentId)
                .eq(CategoryEntity::getCategoryName, categoryName == null ? null : categoryName.trim())
                .ne(excludeId != null, CategoryEntity::getId, excludeId);
        return categoryMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Long save(Category category) {
        CategoryEntity entity = toEntity(category);
        if (entity.getId() == null) {
            categoryMapper.insert(entity);
        } else {
            categoryMapper.updateById(entity);
        }
        category.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void remove(Long id) {
        categoryMapper.deleteById(id);
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CategoryEntity> filter(CategoryQuery query) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            wrapper.like(CategoryEntity::getCategoryName, query.getKeyword().trim());
        }
        if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CategoryEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && query.getParentId() != null) {
            wrapper.eq(CategoryEntity::getParentId, query.getParentId());
        }
        return wrapper.orderByAsc(CategoryEntity::getCategoryLevel)
                .orderByAsc(CategoryEntity::getSortNo)
                .orderByAsc(CategoryEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Category toDomain(CategoryEntity entity) {
        Category category = new Category();
        category.setId(entity.getId());
        category.setParentId(entity.getParentId());
        category.setCategoryName(entity.getCategoryName());
        category.setCategoryLevel(entity.getCategoryLevel());
        category.setSortNo(entity.getSortNo());
        category.setStatus(CategoryStatus.parse(entity.getStatus()));
        category.setRemark(entity.getRemark());
        category.setCreateTime(entity.getCreateTime());
        category.setUpdateTime(entity.getUpdateTime());
        return category;
    }

    /**
     * 领域模型转实体。
     *
     * @param category 领域模型
     * @return 实体
     */
    private CategoryEntity toEntity(Category category) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(category.getId());
        entity.setParentId(category.getParentId() == null ? Category.ROOT_PARENT_ID : category.getParentId());
        entity.setCategoryName(category.getCategoryName());
        entity.setCategoryLevel(category.getCategoryLevel() == null ? 1 : category.getCategoryLevel());
        entity.setSortNo(category.getSortNo() == null ? 0 : category.getSortNo());
        entity.setStatus(category.getStatus() == null ? CategoryStatus.ACTIVE.name() : category.getStatus().name());
        entity.setRemark(category.getRemark());
        return entity;
    }
}
