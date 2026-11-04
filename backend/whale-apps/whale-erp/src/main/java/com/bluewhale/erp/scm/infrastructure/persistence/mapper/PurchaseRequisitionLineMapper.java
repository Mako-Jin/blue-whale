package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseRequisitionLineEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购申请分录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PurchaseRequisitionLineMapper extends BaseMapper<PurchaseRequisitionLineEntity> {
}
