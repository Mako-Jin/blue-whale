package com.bluewhale.oa.document.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocArchiveRefEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公文归档映射 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocArchiveRefMapper extends BaseMapper<DocArchiveRefEntity> {
}
