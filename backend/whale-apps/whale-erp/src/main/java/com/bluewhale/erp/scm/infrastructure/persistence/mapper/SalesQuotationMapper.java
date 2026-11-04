package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesQuotationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售报价单头 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesQuotationMapper extends BaseMapper<SalesQuotationEntity> {
}
