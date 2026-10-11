package com.bluewhale.sts.auth.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.user.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

}
