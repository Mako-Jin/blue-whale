package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.relation.RelUserGroupMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户组-成员仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface RelUserGroupMemberMapper extends BaseMapper<RelUserGroupMemberEntity> {

}
