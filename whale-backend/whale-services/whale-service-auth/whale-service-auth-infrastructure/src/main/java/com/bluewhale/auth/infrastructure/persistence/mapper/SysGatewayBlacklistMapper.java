package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayBlacklistPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关黑名单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysGatewayBlacklistMapper extends BaseMapper<SysGatewayBlacklistPo> {
}
