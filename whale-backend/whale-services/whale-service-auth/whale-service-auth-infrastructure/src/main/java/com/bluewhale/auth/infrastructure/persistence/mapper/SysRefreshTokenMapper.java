package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysRefreshTokenPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 刷新令牌 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysRefreshTokenMapper extends BaseMapper<SysRefreshTokenPo> {
}
