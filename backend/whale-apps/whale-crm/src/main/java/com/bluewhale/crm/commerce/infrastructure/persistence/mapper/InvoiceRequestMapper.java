package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.InvoiceRequestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开票申请 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface InvoiceRequestMapper extends BaseMapper<InvoiceRequestEntity> {
}
