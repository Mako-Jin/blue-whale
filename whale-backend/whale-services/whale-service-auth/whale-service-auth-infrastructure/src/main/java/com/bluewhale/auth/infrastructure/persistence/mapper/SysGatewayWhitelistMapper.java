package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayWhitelistPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关白名单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysGatewayWhitelistMapper extends BaseMapper<SysGatewayWhitelistPo> {
}
