package com.bluewhale.crm.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.CampaignMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 活动人群成员 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface CampaignMemberMapper extends BaseMapper<CampaignMemberEntity> {
}
