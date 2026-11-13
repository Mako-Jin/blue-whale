package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysDevicePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录设备 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysDeviceMapper extends BaseMapper<SysDevicePo> {
}
