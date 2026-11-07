package com.bluewhale.crm.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.CampaignEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 营销活动 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface CampaignMapper extends BaseMapper<CampaignEntity> {
}
