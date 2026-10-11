package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.authentication.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;

/** 刷新令牌仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

}
