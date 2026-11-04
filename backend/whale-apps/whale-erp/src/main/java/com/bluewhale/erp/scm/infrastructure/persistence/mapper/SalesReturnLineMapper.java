package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesReturnLineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售退货分录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesReturnLineMapper extends BaseMapper<SalesReturnLineEntity> {
}
