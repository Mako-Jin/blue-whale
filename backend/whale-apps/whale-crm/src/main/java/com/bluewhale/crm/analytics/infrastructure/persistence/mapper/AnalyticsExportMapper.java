package com.bluewhale.crm.analytics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.AnalyticsExportEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分析导出任务 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AnalyticsExportMapper extends BaseMapper<AnalyticsExportEntity> {
}
