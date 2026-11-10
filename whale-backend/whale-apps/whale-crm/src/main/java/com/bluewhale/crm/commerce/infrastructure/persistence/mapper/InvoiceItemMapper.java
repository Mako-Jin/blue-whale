package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.InvoiceItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开票申请行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface InvoiceItemMapper extends BaseMapper<InvoiceItemEntity> {
}
