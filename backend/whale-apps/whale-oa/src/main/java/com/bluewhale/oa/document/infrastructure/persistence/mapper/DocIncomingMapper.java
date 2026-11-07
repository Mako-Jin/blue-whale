package com.bluewhale.oa.document.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocIncomingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收文 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocIncomingMapper extends BaseMapper<DocIncomingEntity> {
}
