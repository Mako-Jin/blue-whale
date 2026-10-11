package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.relation.RelOrgUserEntity;
import org.apache.ibatis.annotations.Mapper;

/** 组织-用户仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface RelOrgUserMapper extends BaseMapper<RelOrgUserEntity> {

}
