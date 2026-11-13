package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayRouteOverridePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关路由业务属性覆盖 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysGatewayRouteOverrideMapper extends BaseMapper<SysGatewayRouteOverridePo> {
}
