package com.bluewhale.oa.expense.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseInvoiceEntity;
import org.apache.ibatis.annotations.Delete;

/**
 * 报销发票 Mapper。
 *
 * <p>发票整单替换需物理删除旧行，避免逻辑删除行占用 {@code (invoice_code, invoice_no)} 唯一键。</p>
 *
 * @author BlueWhale
 */
public interface ExpenseInvoiceMapper extends BaseMapper<ExpenseInvoiceEntity> {

    /**
     * 物理删除指定报销单的全部发票。
     *
     * @param expenseId 报销单ID
     * @return 删除条数
     */
    @Delete("DELETE FROM oa_expense_invoice WHERE expense_id = #{expenseId}")
    int deleteByExpenseId(Long expenseId);
}
