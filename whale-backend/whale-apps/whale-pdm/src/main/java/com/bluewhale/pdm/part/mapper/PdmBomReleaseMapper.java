package com.bluewhale.pdm.part.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.part.entity.PdmBomRelease;
import org.apache.ibatis.annotations.Mapper;

/**
 * BOM 发布快照 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmBomReleaseMapper extends BaseMapper<PdmBomRelease> {
}
