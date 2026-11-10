package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseReturnLineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购退货分录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PurchaseReturnLineMapper extends BaseMapper<PurchaseReturnLineEntity> {
}
