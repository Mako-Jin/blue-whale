package com.bluewhale.oa.analytics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.analytics.infrastructure.persistence.entity.ReportSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表快照 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ReportSnapshotMapper extends BaseMapper<ReportSnapshotEntity> {
}
