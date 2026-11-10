package com.bluewhale.pdm.part.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.part.entity.PdmBomLine;
import org.apache.ibatis.annotations.Mapper;

/**
 * BOM 行 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmBomLineMapper extends BaseMapper<PdmBomLine> {
}
