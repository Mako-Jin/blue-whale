package com.bluewhale.lms.integration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.integration.infrastructure.persistence.entity.NotifyLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知投递留痕 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface NotifyLogMapper extends BaseMapper<NotifyLogEntity> {
}
