package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysBrandConfigPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 品牌配置 Mapper（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysBrandConfigMapper extends BaseMapper<SysBrandConfigPo> {
}
