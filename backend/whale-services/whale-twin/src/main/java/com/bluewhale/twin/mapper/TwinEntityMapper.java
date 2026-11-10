package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 孪生实体 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinEntityMapper extends BaseMapper<TwinEntity> {
}
