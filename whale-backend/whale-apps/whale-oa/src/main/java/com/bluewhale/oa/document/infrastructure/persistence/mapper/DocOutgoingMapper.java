package com.bluewhale.oa.document.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocOutgoingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发文 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocOutgoingMapper extends BaseMapper<DocOutgoingEntity> {
}
