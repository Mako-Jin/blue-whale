package com.bluewhale.sts.auth.resource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import org.apache.ibatis.annotations.Mapper;

/** 资源仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。 */
@Mapper
public interface ResourceMapper extends BaseMapper<ResourceEntity> {

}
