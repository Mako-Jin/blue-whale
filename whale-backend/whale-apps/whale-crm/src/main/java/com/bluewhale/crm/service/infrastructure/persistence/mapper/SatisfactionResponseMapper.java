package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.SatisfactionResponseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 满意度答卷 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SatisfactionResponseMapper extends BaseMapper<SatisfactionResponseEntity> {
}
