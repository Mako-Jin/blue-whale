package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.relation.RelRoleResourceInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

/** 角色-资源实例仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface RelRoleResourceInstanceMapper extends BaseMapper<RelRoleResourceInstanceEntity> {

}
