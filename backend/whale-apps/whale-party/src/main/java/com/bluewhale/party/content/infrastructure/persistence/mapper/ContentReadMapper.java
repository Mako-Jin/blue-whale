package com.bluewhale.party.content.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.content.infrastructure.persistence.entity.ContentReadEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党建内容阅读记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ContentReadMapper extends BaseMapper<ContentReadEntity> {
}
