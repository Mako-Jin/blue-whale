package com.bluewhale.mall.pricing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.pricing.infrastructure.persistence.entity.FreightTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运费模板 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface FreightTemplateMapper extends BaseMapper<FreightTemplateEntity> {
}
