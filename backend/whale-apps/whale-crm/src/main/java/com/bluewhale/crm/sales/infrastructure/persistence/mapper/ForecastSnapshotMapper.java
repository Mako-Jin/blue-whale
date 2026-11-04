package com.bluewhale.crm.sales.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.sales.infrastructure.persistence.entity.ForecastSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 销售预测快照 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForecastSnapshotMapper extends BaseMapper<ForecastSnapshotEntity> {
}
