package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysPermissionStrategyPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限策略 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysPermissionStrategyMapper extends BaseMapper<SysPermissionStrategyPo> {
}
