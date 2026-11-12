package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.entity.SysTenantConfigPo;

/**
 * 租户配置 Mapper（MyBatis-Plus）。
 *
 * <p>不标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")}
 * 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface SysTenantConfigMapper extends BaseMapper<SysTenantConfigPo> {
}
