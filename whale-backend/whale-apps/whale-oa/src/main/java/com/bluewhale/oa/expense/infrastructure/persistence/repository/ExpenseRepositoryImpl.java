package com.bluewhale.oa.expense.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.expense.domain.model.ErpPushStatus;
import com.bluewhale.oa.expense.domain.model.Expense;
import com.bluewhale.oa.expense.domain.model.ExpenseInvoice;
import com.bluewhale.oa.expense.domain.model.ExpenseItem;
import com.bluewhale.oa.expense.domain.model.ExpenseQuery;
import com.bluewhale.oa.expense.domain.model.ExpenseStatus;
import com.bluewhale.oa.expense.domain.repository.ExpenseRepository;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseEntity;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseInvoiceEntity;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseItemEntity;
import com.bluewhale.oa.expense.infrastructure.persistence.mapper.ExpenseInvoiceMapper;
import com.bluewhale.oa.expense.infrastructure.persistence.mapper.ExpenseItemMapper;
import com.bluewhale.oa.expense.infrastructure.persistence.mapper.ExpenseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 报销单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ExpenseRepositoryImpl implements ExpenseRepository {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final ExpenseMapper expenseMapper;

    private final ExpenseItemMapper itemMapper;

    private final ExpenseInvoiceMapper invoiceMapper;

    @Override
    public List<Expense> page(ExpenseQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ExpenseEntity> page = expenseMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), size, false), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ExpenseQuery query) {
        Long total = expenseMapper.selectCount(filter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<Expense> findById(Long id) {
        return Optional.ofNullable(expenseMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String code, Long excludeId) {
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<ExpenseEntity>()
                .eq(ExpenseEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(ExpenseEntity::getId, excludeId);
        }
        Long total = expenseMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public void insert(Expense expense) {
        ExpenseEntity entity = new ExpenseEntity();
        fill(entity, expense);
        expenseMapper.insert(entity);
        expense.setId(entity.getId());
        expense.setCreateTime(entity.getCreateTime());
        expense.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(Expense expense) {
        expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseEntity>()
                .eq(ExpenseEntity::getId, expense.getId())
                .set(ExpenseEntity::getCode, expense.getCode())
                .set(ExpenseEntity::getApplicant, expense.getApplicant())
                .set(ExpenseEntity::getApplicantId, expense.getApplicantId())
                .set(ExpenseEntity::getDeptId, expense.getDeptId())
                .set(ExpenseEntity::getCategoryCode, expense.getCategoryCode())
                .set(ExpenseEntity::getTitle, expense.getTitle())
                .set(ExpenseEntity::getTotalAmount, expense.getTotalAmount())
                .set(ExpenseEntity::getCurrency, expense.getCurrency())
                .set(ExpenseEntity::getStatus,
                        expense.getStatus() == null ? null : expense.getStatus().name())
                .set(ExpenseEntity::getApprovalInstanceId, expense.getApprovalInstanceId())
                .set(ExpenseEntity::getErpPushStatus,
                        expense.getErpPushStatus() == null ? null : expense.getErpPushStatus().name())
                .set(ExpenseEntity::getErpPayableNo, expense.getErpPayableNo())
                .set(ExpenseEntity::getTripId, expense.getTripId())
                .set(ExpenseEntity::getTripBudgetAmount, expense.getTripBudgetAmount() == null
                        ? java.math.BigDecimal.ZERO : expense.getTripBudgetAmount())
                .set(ExpenseEntity::getSubmitTime, expense.getSubmitTime())
                .set(ExpenseEntity::getRemark, expense.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        expenseMapper.deleteById(id);
    }

    @Override
    public List<ExpenseItem> listItems(Long expenseId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ExpenseItemEntity>()
                        .eq(ExpenseItemEntity::getExpenseId, expenseId)
                        .orderByAsc(ExpenseItemEntity::getLineNo))
                .stream().map(ExpenseRepositoryImpl::toItem).toList();
    }

    @Override
    public void replaceItems(Long expenseId, List<ExpenseItem> items) {
        itemMapper.deleteByExpenseId(expenseId);
        if (items == null) {
            return;
        }
        int lineNo = 1;
        for (ExpenseItem item : items) {
            ExpenseItemEntity entity = new ExpenseItemEntity();
            entity.setExpenseId(expenseId);
            entity.setLineNo(item.getLineNo() == null ? lineNo : item.getLineNo());
            entity.setCategoryCode(item.getCategoryCode());
            entity.setExpenseDate(item.getExpenseDate());
            entity.setAmount(item.getAmount());
            entity.setTaxAmount(item.getTaxAmount() == null ? java.math.BigDecimal.ZERO : item.getTaxAmount());
            entity.setDescription(item.getDescription());
            itemMapper.insert(entity);
            lineNo++;
        }
    }

    @Override
    public List<ExpenseInvoice> listInvoices(Long expenseId) {
        return invoiceMapper.selectList(new LambdaQueryWrapper<ExpenseInvoiceEntity>()
                        .eq(ExpenseInvoiceEntity::getExpenseId, expenseId)
                        .orderByAsc(ExpenseInvoiceEntity::getId))
                .stream().map(ExpenseRepositoryImpl::toInvoice).toList();
    }

    @Override
    public void replaceInvoices(Long expenseId, List<ExpenseInvoice> invoices) {
        invoiceMapper.deleteByExpenseId(expenseId);
        if (invoices == null) {
            return;
        }
        for (ExpenseInvoice invoice : invoices) {
            ExpenseInvoiceEntity entity = new ExpenseInvoiceEntity();
            entity.setExpenseId(expenseId);
            entity.setInvoiceCode(invoice.getInvoiceCode());
            entity.setInvoiceNo(invoice.getInvoiceNo());
            entity.setInvoiceDate(invoice.getInvoiceDate());
            entity.setAmount(invoice.getAmount());
            entity.setTaxAmount(invoice.getTaxAmount() == null ? java.math.BigDecimal.ZERO : invoice.getTaxAmount());
            entity.setFileId(invoice.getFileId());
            invoiceMapper.insert(entity);
        }
    }

    @Override
    public boolean existsInvoice(String invoiceCode, String invoiceNo, Long excludeExpenseId) {
        LambdaQueryWrapper<ExpenseInvoiceEntity> wrapper = new LambdaQueryWrapper<ExpenseInvoiceEntity>()
                .eq(ExpenseInvoiceEntity::getInvoiceNo, invoiceNo);
        if (StringUtils.hasText(invoiceCode)) {
            wrapper.eq(ExpenseInvoiceEntity::getInvoiceCode, invoiceCode);
        } else {
            wrapper.isNull(ExpenseInvoiceEntity::getInvoiceCode);
        }
        if (excludeExpenseId != null) {
            wrapper.ne(ExpenseInvoiceEntity::getExpenseId, excludeExpenseId);
        }
        Long total = invoiceMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public List<Expense> listPushable(int limit) {
        Page<ExpenseEntity> page = expenseMapper.selectPage(new Page<>(1L, Math.max(limit, 1), false),
                new LambdaQueryWrapper<ExpenseEntity>()
                        .eq(ExpenseEntity::getStatus, ExpenseStatus.APPROVED.name())
                        .in(ExpenseEntity::getErpPushStatus,
                                ErpPushStatus.PENDING.name(), ErpPushStatus.FAILED.name())
                        .orderByAsc(ExpenseEntity::getUpdateTime));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ExpenseEntity> filter(ExpenseQuery query) {
        return new LambdaQueryWrapper<ExpenseEntity>()
                .like(StringUtils.hasText(query.getCode()), ExpenseEntity::getCode, query.getCode())
                .eq(StringUtils.hasText(query.getApplicant()),
                        ExpenseEntity::getApplicant, query.getApplicant())
                .eq(query.getApplicantId() != null, ExpenseEntity::getApplicantId, query.getApplicantId())
                .eq(StringUtils.hasText(query.getCategoryCode()),
                        ExpenseEntity::getCategoryCode, query.getCategoryCode())
                .eq(StringUtils.hasText(query.getStatus()), ExpenseEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getErpPushStatus()),
                        ExpenseEntity::getErpPushStatus, query.getErpPushStatus())
                .ge(query.getDateFrom() != null, ExpenseEntity::getSubmitTime,
                        query.getDateFrom() == null ? null : query.getDateFrom().atStartOfDay())
                .le(query.getDateTo() != null, ExpenseEntity::getSubmitTime,
                        query.getDateTo() == null ? null
                                : LocalDateTime.of(query.getDateTo(), java.time.LocalTime.MAX))
                .orderByDesc(ExpenseEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 报销单实体
     * @return 报销单模型
     */
    private Expense toDomain(ExpenseEntity entity) {
        Expense expense = new Expense();
        expense.setId(entity.getId());
        expense.setCode(entity.getCode());
        expense.setApplicant(entity.getApplicant());
        expense.setApplicantId(entity.getApplicantId());
        expense.setDeptId(entity.getDeptId());
        expense.setCategoryCode(entity.getCategoryCode());
        expense.setTitle(entity.getTitle());
        expense.setTotalAmount(entity.getTotalAmount());
        expense.setCurrency(entity.getCurrency());
        expense.setStatus(entity.getStatus() == null
                ? ExpenseStatus.PENDING : ExpenseStatus.of(entity.getStatus()));
        expense.setApprovalInstanceId(entity.getApprovalInstanceId());
        expense.setErpPushStatus(entity.getErpPushStatus() == null
                ? ErpPushStatus.NONE : ErpPushStatus.of(entity.getErpPushStatus()));
        expense.setErpPayableNo(entity.getErpPayableNo());
        expense.setTripId(entity.getTripId());
        expense.setTripBudgetAmount(entity.getTripBudgetAmount() == null
                ? java.math.BigDecimal.ZERO : entity.getTripBudgetAmount());
        expense.setSubmitTime(entity.getSubmitTime());
        expense.setRemark(entity.getRemark());
        expense.setCreateTime(entity.getCreateTime());
        expense.setUpdateTime(entity.getUpdateTime());
        return expense;
    }

    /**
     * 明细实体转领域模型。
     *
     * @param entity 明细实体
     * @return 明细模型
     */
    private static ExpenseItem toItem(ExpenseItemEntity entity) {
        ExpenseItem item = new ExpenseItem();
        item.setId(entity.getId());
        item.setExpenseId(entity.getExpenseId());
        item.setLineNo(entity.getLineNo());
        item.setCategoryCode(entity.getCategoryCode());
        item.setExpenseDate(entity.getExpenseDate());
        item.setAmount(entity.getAmount());
        item.setTaxAmount(entity.getTaxAmount());
        item.setDescription(entity.getDescription());
        return item;
    }

    /**
     * 发票实体转领域模型。
     *
     * @param entity 发票实体
     * @return 发票模型
     */
    private static ExpenseInvoice toInvoice(ExpenseInvoiceEntity entity) {
        ExpenseInvoice invoice = new ExpenseInvoice();
        invoice.setId(entity.getId());
        invoice.setExpenseId(entity.getExpenseId());
        invoice.setInvoiceCode(entity.getInvoiceCode());
        invoice.setInvoiceNo(entity.getInvoiceNo());
        invoice.setInvoiceDate(entity.getInvoiceDate());
        invoice.setAmount(entity.getAmount());
        invoice.setTaxAmount(entity.getTaxAmount());
        invoice.setFileId(entity.getFileId());
        return invoice;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 报销单实体
     * @param expense 报销单模型
     */
    private void fill(ExpenseEntity entity, Expense expense) {
        entity.setCode(expense.getCode());
        entity.setApplicant(expense.getApplicant());
        entity.setApplicantId(expense.getApplicantId());
        entity.setDeptId(expense.getDeptId());
        entity.setCategoryCode(expense.getCategoryCode());
        entity.setTitle(expense.getTitle());
        entity.setTotalAmount(expense.getTotalAmount() == null
                ? java.math.BigDecimal.ZERO : expense.getTotalAmount());
        entity.setCurrency(StringUtils.hasText(expense.getCurrency()) ? expense.getCurrency() : "CNY");
        entity.setStatus(expense.getStatus() == null ? ExpenseStatus.PENDING.name() : expense.getStatus().name());
        entity.setApprovalInstanceId(expense.getApprovalInstanceId());
        entity.setErpPushStatus(expense.getErpPushStatus() == null
                ? ErpPushStatus.NONE.name() : expense.getErpPushStatus().name());
        entity.setErpPayableNo(expense.getErpPayableNo());
        entity.setTripId(expense.getTripId());
        entity.setTripBudgetAmount(expense.getTripBudgetAmount() == null
                ? java.math.BigDecimal.ZERO : expense.getTripBudgetAmount());
        entity.setSubmitTime(expense.getSubmitTime());
        entity.setRemark(expense.getRemark());
    }
}
