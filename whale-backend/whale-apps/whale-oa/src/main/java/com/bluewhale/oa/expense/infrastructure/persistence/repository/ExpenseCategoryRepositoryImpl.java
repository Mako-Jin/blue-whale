package com.bluewhale.oa.expense.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.oa.expense.domain.model.ExpenseCategory;
import com.bluewhale.oa.expense.domain.model.ExpenseStandard;
import com.bluewhale.oa.expense.domain.repository.ExpenseCategoryRepository;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseCategoryEntity;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseStandardEntity;
import com.bluewhale.oa.expense.infrastructure.persistence.mapper.ExpenseCategoryMapper;
import com.bluewhale.oa.expense.infrastructure.persistence.mapper.ExpenseStandardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 费用类别与费用标准仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ExpenseCategoryRepositoryImpl implements ExpenseCategoryRepository {

    private final ExpenseCategoryMapper categoryMapper;

    private final ExpenseStandardMapper standardMapper;

    @Override
    public List<ExpenseCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ExpenseCategoryEntity>()
                        .orderByAsc(ExpenseCategoryEntity::getSortNo)
                        .orderByAsc(ExpenseCategoryEntity::getId))
                .stream().map(ExpenseCategoryRepositoryImpl::toCategory).toList();
    }

    @Override
    public Optional<ExpenseCategory> findCategoryById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id))
                .map(ExpenseCategoryRepositoryImpl::toCategory);
    }

    @Override
    public Optional<ExpenseCategory> findCategoryByCode(String code) {
        return categoryMapper.selectList(new LambdaQueryWrapper<ExpenseCategoryEntity>()
                        .eq(ExpenseCategoryEntity::getCode, code))
                .stream().findFirst().map(ExpenseCategoryRepositoryImpl::toCategory);
    }

    @Override
    public void saveCategory(ExpenseCategory category) {
        if (category.getId() == null) {
            ExpenseCategoryEntity entity = new ExpenseCategoryEntity();
            fillCategory(entity, category);
            categoryMapper.insert(entity);
            category.setId(entity.getId());
            return;
        }
        LambdaUpdateWrapper<ExpenseCategoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExpenseCategoryEntity::getId, category.getId());
        wrapper.set(ExpenseCategoryEntity::getCode, category.getCode());
        wrapper.set(ExpenseCategoryEntity::getName, category.getName());
        wrapper.set(ExpenseCategoryEntity::getAccountSubject, category.getAccountSubject());
        wrapper.set(ExpenseCategoryEntity::getRequireInvoice, flag(category.getRequireInvoice()));
        wrapper.set(ExpenseCategoryEntity::getSingleLimit, category.getSingleLimit());
        wrapper.set(ExpenseCategoryEntity::getSortNo, category.getSortNo() == null ? 0 : category.getSortNo());
        wrapper.set(ExpenseCategoryEntity::getEnabled, flag(category.getEnabled()));
        categoryMapper.update(null, wrapper);
    }

    @Override
    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    @Override
    public boolean existsCategoryCode(String code, Long excludeId) {
        LambdaQueryWrapper<ExpenseCategoryEntity> wrapper = new LambdaQueryWrapper<ExpenseCategoryEntity>()
                .eq(ExpenseCategoryEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(ExpenseCategoryEntity::getId, excludeId);
        }
        Long total = categoryMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public List<ExpenseStandard> listStandards(Long categoryId) {
        return standardMapper.selectList(new LambdaQueryWrapper<ExpenseStandardEntity>()
                        .eq(ExpenseStandardEntity::getCategoryId, categoryId)
                        .orderByAsc(ExpenseStandardEntity::getLevelCode))
                .stream().map(ExpenseCategoryRepositoryImpl::toStandard).toList();
    }

    @Override
    public Optional<ExpenseStandard> findStandard(Long categoryId, String levelCode) {
        return standardMapper.selectList(new LambdaQueryWrapper<ExpenseStandardEntity>()
                        .eq(ExpenseStandardEntity::getCategoryId, categoryId)
                        .eq(ExpenseStandardEntity::getLevelCode, levelCode))
                .stream().findFirst().map(ExpenseCategoryRepositoryImpl::toStandard);
    }

    @Override
    public Optional<ExpenseStandard> findStandardById(Long id) {
        return Optional.ofNullable(standardMapper.selectById(id))
                .map(ExpenseCategoryRepositoryImpl::toStandard);
    }

    @Override
    public void saveStandard(ExpenseStandard standard) {
        if (standard.getId() == null) {
            ExpenseStandardEntity entity = new ExpenseStandardEntity();
            fillStandard(entity, standard);
            standardMapper.insert(entity);
            standard.setId(entity.getId());
        } else {
            standardMapper.update(null, new LambdaUpdateWrapper<ExpenseStandardEntity>()
                    .eq(ExpenseStandardEntity::getId, standard.getId())
                    .set(ExpenseStandardEntity::getCategoryId, standard.getCategoryId())
                    .set(ExpenseStandardEntity::getLevelCode, standard.getLevelCode())
                    .set(ExpenseStandardEntity::getLimitAmount, standard.getLimitAmount())
                    .set(ExpenseStandardEntity::getEnabled, flag(standard.getEnabled()))
                    .set(ExpenseStandardEntity::getRemark, standard.getRemark()));
        }
    }

    @Override
    public void deleteStandard(Long id) {
        standardMapper.deleteById(id);
    }

    /**
     * 布尔转 0/1，空值默认启用。
     *
     * @param value 布尔值
     * @return 0/1
     */
    private static int flag(Boolean value) {
        return value == null || value ? 1 : 0;
    }

    /**
     * 费用类别实体转领域模型。
     *
     * @param entity 类别实体
     * @return 类别模型
     */
    private static ExpenseCategory toCategory(ExpenseCategoryEntity entity) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(entity.getId());
        category.setCode(entity.getCode());
        category.setName(entity.getName());
        category.setAccountSubject(entity.getAccountSubject());
        category.setRequireInvoice(entity.getRequireInvoice() != null && entity.getRequireInvoice() == 1);
        category.setSingleLimit(entity.getSingleLimit());
        category.setSortNo(entity.getSortNo());
        category.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        category.setCreateTime(entity.getCreateTime());
        category.setUpdateTime(entity.getUpdateTime());
        return category;
    }

    /**
     * 费用标准实体转领域模型。
     *
     * @param entity 标准实体
     * @return 标准模型
     */
    private static ExpenseStandard toStandard(ExpenseStandardEntity entity) {
        ExpenseStandard standard = new ExpenseStandard();
        standard.setId(entity.getId());
        standard.setCategoryId(entity.getCategoryId());
        standard.setLevelCode(entity.getLevelCode());
        standard.setLimitAmount(entity.getLimitAmount());
        standard.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        standard.setRemark(entity.getRemark());
        return standard;
    }

    /**
     * 领域模型写入类别实体。
     *
     * @param entity 类别实体
     * @param category 类别模型
     */
    private static void fillCategory(ExpenseCategoryEntity entity, ExpenseCategory category) {
        entity.setCode(category.getCode());
        entity.setName(category.getName());
        entity.setAccountSubject(category.getAccountSubject());
        entity.setRequireInvoice(flag(category.getRequireInvoice()));
        entity.setSingleLimit(category.getSingleLimit());
        entity.setSortNo(category.getSortNo() == null ? 0 : category.getSortNo());
        entity.setEnabled(flag(category.getEnabled()));
    }

    /**
     * 领域模型写入标准实体。
     *
     * @param entity 标准实体
     * @param standard 标准模型
     */
    private static void fillStandard(ExpenseStandardEntity entity, ExpenseStandard standard) {
        entity.setCategoryId(standard.getCategoryId());
        entity.setLevelCode(standard.getLevelCode());
        entity.setLimitAmount(standard.getLimitAmount());
        entity.setEnabled(flag(standard.getEnabled()));
        entity.setRemark(standard.getRemark());
    }
}
