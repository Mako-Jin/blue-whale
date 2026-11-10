package com.bluewhale.pdm.part.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.part.entity.PdmBomDiffTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * BOM 异步对比任务 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmBomDiffTaskMapper extends BaseMapper<PdmBomDiffTask> {
}
