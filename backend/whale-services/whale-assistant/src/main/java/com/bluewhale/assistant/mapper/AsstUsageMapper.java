package com.bluewhale.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.assistant.entity.AsstUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 助手用量日聚合 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AsstUsageMapper extends BaseMapper<AsstUsage> {
}
