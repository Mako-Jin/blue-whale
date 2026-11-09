package com.bluewhale.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.iot.entity.IotProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 产品 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface IotProductMapper extends BaseMapper<IotProduct> {
}
