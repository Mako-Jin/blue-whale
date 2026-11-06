package com.bluewhale.party.content.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.content.infrastructure.persistence.entity.ContentAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党建内容审核轨迹 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ContentAuditMapper extends BaseMapper<ContentAuditEntity> {
}
