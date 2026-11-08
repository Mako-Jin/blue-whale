package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteLineEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 报价行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface QuoteLineMapper extends BaseMapper<QuoteLineEntity> {

    /**
     * 按报价 ID 物理删除报价行。
     *
     * <p>行表挂 BaseEntity 的 {@code @TableLogic}，BaseMapper.delete 走逻辑删除（deleted=1），
     * 旧行仍物理存在，会顶住报价行唯一键，导致 replaceLines 重插冲突；
     * 明细行随主单留痕即可，这里用原生 SQL 物理删除（含历史残留行）。</p>
     *
     * @param quoteId 报价 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM crm_quote_line WHERE quote_id = #{quoteId}")
    int deletePhysicallyByQuoteId(@Param("quoteId") Long quoteId);
}
