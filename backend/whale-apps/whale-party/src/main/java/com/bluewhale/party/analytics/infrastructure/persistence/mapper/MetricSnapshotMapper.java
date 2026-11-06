package com.bluewhale.party.analytics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.analytics.infrastructure.persistence.entity.MetricSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统计快照 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshotEntity> {
}
