package com.bluewhale.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.iot.entity.IotTelemetry;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 遥测 Mapper（只追加写入 + 设备视角范围查询）。
 *
 * @author BlueWhale
 */
@Mapper
public interface IotTelemetryMapper extends BaseMapper<IotTelemetry> {
}
