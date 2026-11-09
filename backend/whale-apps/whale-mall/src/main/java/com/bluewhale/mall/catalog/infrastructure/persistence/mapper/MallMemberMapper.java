package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.MallMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商城会员 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface MallMemberMapper extends BaseMapper<MallMemberEntity> {
}
