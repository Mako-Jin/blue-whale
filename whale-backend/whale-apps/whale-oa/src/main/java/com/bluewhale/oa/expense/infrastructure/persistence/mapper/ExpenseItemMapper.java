package com.bluewhale.oa.expense.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.expense.infrastructure.persistence.entity.ExpenseItemEntity;
import org.apache.ibatis.annotations.Delete;

/**
 * 报销明细 Mapper。
 *
 * <p>明细整单替换需物理删除旧行，避免逻辑删除行占用 {@code (expense_id, line_no)} 唯一键。</p>
 *
 * @author BlueWhale
 */
public interface ExpenseItemMapper extends BaseMapper<ExpenseItemEntity> {

    /**
     * 物理删除指定报销单的全部明细。
     *
     * @param expenseId 报销单ID
     * @return 删除条数
     */
    @Delete("DELETE FROM oa_expense_item WHERE expense_id = #{expenseId}")
    int deleteByExpenseId(Long expenseId);
}
