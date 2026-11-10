package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinAlertDispatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警外发投递记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinAlertDispatchMapper extends BaseMapper<TwinAlertDispatch> {
}
