package com.bluewhale.mall.channel.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.channel.infrastructure.persistence.entity.ChannelEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ChannelMapper extends BaseMapper<ChannelEntity> {
}
