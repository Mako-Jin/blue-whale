package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 孪生阈值告警 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinAlertMapper extends BaseMapper<TwinAlert> {
}
