package com.bluewhale.oa.analytics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.oa.analytics.infrastructure.persistence.entity.ReportExportLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表导出记录 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ReportExportLogMapper extends BaseMapper<ReportExportLogEntity> {
}
