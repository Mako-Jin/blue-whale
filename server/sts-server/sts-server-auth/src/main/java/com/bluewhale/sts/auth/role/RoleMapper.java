package com.bluewhale.sts.auth.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.role.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

/** 角色仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

}
