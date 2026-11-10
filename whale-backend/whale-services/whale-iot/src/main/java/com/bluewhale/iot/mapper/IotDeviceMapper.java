package com.bluewhale.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.iot.entity.IotDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 设备 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface IotDeviceMapper extends BaseMapper<IotDevice> {
}
