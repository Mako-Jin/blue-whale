package com.bluewhale.sts.auth.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.tenant.TenantEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户仓储。
 *
 * <p>{@code @Mapper} 是**必须显式标注**的：本模块的 Mapper 扫描配了
 * {@code annotationClass = Mapper.class}，不标注就不会被注册 ——
 * 这是有意为之，避免包内任何接口都被当成 Mapper。
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

}
