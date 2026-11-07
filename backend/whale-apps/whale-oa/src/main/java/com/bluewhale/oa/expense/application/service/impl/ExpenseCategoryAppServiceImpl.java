package com.bluewhale.oa.expense.application.service.impl;

import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.expense.application.command.ExpenseCommands;
import com.bluewhale.oa.expense.application.service.ExpenseCategoryAppService;
import com.bluewhale.oa.expense.application.view.ExpenseViews;
import com.bluewhale.oa.expense.domain.model.ExpenseCategory;
import com.bluewhale.oa.expense.domain.model.ExpenseStandard;
import com.bluewhale.oa.expense.domain.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 费用类别与费用标准应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryAppServiceImpl implements ExpenseCategoryAppService {

    private final ExpenseCategoryRepository categoryRepository;

    @Override
    public List<ExpenseViews.CategoryView> listCategories() {
        return categoryRepository.listCategories().stream().map(ExpenseViews.CategoryView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseViews.CategoryView saveCategory(ExpenseCommands.CategorySaveCommand command) {
        if (categoryRepository.existsCategoryCode(command.getCode(), command.getId())) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_EXISTS,
                    "费用类别编码已存在：" + command.getCode());
        }
        ExpenseCategory category = new ExpenseCategory();
        if (command.getId() != null) {
            category = categoryRepository.findCategoryById(command.getId())
                    .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                            "费用类别不存在，id=" + command.getId()));
        }
        category.setCode(command.getCode());
        category.setName(command.getName());
        category.setAccountSubject(command.getAccountSubject());
        category.setRequireInvoice(command.getRequireInvoice());
        category.setSingleLimit(command.getSingleLimit());
        category.setSortNo(command.getSortNo());
        category.setEnabled(command.getEnabled());
        categoryRepository.saveCategory(category);
        log.info("费用类别已保存: id={}, code={}", category.getId(), category.getCode());
        return ExpenseViews.CategoryView.from(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        categoryRepository.findCategoryById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                        "费用类别不存在，id=" + id));
        categoryRepository.deleteCategory(id);
        log.info("费用类别已删除: id={}", id);
    }

    @Override
    public List<ExpenseViews.StandardView> listStandards(Long categoryId) {
        return categoryRepository.listStandards(categoryId).stream()
                .map(ExpenseViews.StandardView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseViews.StandardView saveStandard(ExpenseCommands.StandardSaveCommand command) {
        ExpenseCategory category = categoryRepository.findCategoryById(command.getCategoryId())
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                        "费用类别不存在，id=" + command.getCategoryId()));
        ExpenseStandard standard = command.getId() == null
                ? new ExpenseStandard() : requireStandard(command.getId());
        ExpenseStandard existing = categoryRepository
                .findStandard(category.getId(), command.getLevelCode()).orElse(null);
        if (existing != null && !existing.getId().equals(standard.getId())) {
            throw OaErrorCode.exception(OaErrorCode.EXPENSE_INVALID, "该类别下适用范围标准已存在");
        }
        standard.setCategoryId(category.getId());
        standard.setLevelCode(command.getLevelCode());
        standard.setLimitAmount(command.getLimitAmount());
        standard.setEnabled(command.getEnabled());
        standard.setRemark(command.getRemark());
        categoryRepository.saveStandard(standard);
        log.info("费用标准已保存: id={}, categoryId={}, level={}", standard.getId(),
                standard.getCategoryId(), standard.getLevelCode());
        return ExpenseViews.StandardView.from(standard);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStandard(Long id) {
        requireStandard(id);
        categoryRepository.deleteStandard(id);
        log.info("费用标准已删除: id={}", id);
    }

    /**
     * 查询费用标准，缺失时抛业务异常。
     *
     * @param id 主键ID
     * @return 费用标准
     */
    private ExpenseStandard requireStandard(Long id) {
        return categoryRepository.findStandardById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                        "费用标准不存在，id=" + id));
    }
}
