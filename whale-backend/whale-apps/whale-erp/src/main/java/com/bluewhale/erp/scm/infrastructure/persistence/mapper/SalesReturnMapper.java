package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesReturnEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售退货单头 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesReturnMapper extends BaseMapper<SalesReturnEntity> {
}
