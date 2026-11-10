package com.bluewhale.crm.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.PartnerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道伙伴 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PartnerMapper extends BaseMapper<PartnerEntity> {
}
