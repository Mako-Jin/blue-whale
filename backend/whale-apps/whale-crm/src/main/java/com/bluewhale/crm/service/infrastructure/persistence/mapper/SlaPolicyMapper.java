package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.SlaPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * SLA 策略 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SlaPolicyMapper extends BaseMapper<SlaPolicyEntity> {
}
