package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 报价头 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface QuoteMapper extends BaseMapper<QuoteEntity> {

    /**
     * 查询指定编号前缀的最大序号。
     *
     * @param prefix 编号前缀（如 {@code QUOTE-2026-}）
     * @return 最大序号；无记录返回 0
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(quote_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) "
            + "FROM crm_quote WHERE quote_no LIKE CONCAT(#{prefix}, '%')")
    long maxSequence(@Param("prefix") String prefix);
}
