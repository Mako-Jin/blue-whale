package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseReturnEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购退货单头 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PurchaseReturnMapper extends BaseMapper<PurchaseReturnEntity> {
}
