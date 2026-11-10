package com.bluewhale.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.iot.entity.IotRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 规则 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface IotRuleMapper extends BaseMapper<IotRule> {
}
