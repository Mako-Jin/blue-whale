package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 类目 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
