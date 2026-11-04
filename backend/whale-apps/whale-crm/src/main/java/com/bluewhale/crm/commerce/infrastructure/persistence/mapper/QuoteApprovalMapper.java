package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.QuoteApprovalEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报价审批记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface QuoteApprovalMapper extends BaseMapper<QuoteApprovalEntity> {
}
