package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesQuotationLineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售报价分录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesQuotationLineMapper extends BaseMapper<SalesQuotationLineEntity> {
}
