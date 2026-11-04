package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteLineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报价行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface QuoteLineMapper extends BaseMapper<QuoteLineEntity> {
}
