package com.bluewhale.pdm.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图文档版本 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmDocVersionMapper extends BaseMapper<PdmDocVersion> {
}
