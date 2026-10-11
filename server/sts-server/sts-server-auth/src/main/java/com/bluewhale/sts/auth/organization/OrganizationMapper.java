package com.bluewhale.sts.auth.organization;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.organization.OrganizationEntity;
import org.apache.ibatis.annotations.Mapper;

/** 组织仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {

}
