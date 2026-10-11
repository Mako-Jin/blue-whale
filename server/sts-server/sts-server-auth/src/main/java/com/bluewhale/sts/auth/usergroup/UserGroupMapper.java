package com.bluewhale.sts.auth.usergroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.usergroup.UserGroupEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户组仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface UserGroupMapper extends BaseMapper<UserGroupEntity> {

}
