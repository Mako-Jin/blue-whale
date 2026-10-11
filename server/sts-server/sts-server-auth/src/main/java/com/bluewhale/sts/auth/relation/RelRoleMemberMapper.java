package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.relation.RelRoleMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/** 角色-成员仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface RelRoleMemberMapper extends BaseMapper<RelRoleMemberEntity> {

}
