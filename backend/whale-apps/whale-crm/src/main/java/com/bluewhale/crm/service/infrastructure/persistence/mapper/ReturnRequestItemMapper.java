package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnRequestItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退换货申请行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ReturnRequestItemMapper extends BaseMapper<ReturnRequestItemEntity> {
}
