package com.bluewhale.mes.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mes.quality.entity.MesNonconformingRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 不合格品记录 NCR Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface MesNonconformingRecordMapper extends BaseMapper<MesNonconformingRecord> {
}
