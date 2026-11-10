package com.bluewhale.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.iot.entity.IotCommand;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 指令 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface IotCommandMapper extends BaseMapper<IotCommand> {
}
