package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysOAuthClientPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 客户端 Mapper（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysOAuthClientMapper extends BaseMapper<SysOAuthClientPo> {
}
