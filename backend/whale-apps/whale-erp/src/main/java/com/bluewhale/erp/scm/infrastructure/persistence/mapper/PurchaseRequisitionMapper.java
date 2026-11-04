package com.bluewhale.erp.scm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseRequisitionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购申请单头 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PurchaseRequisitionMapper extends BaseMapper<PurchaseRequisitionEntity> {
}
