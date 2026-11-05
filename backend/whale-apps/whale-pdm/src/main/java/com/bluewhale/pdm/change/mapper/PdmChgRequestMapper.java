package com.bluewhale.pdm.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.change.entity.PdmChgRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 变更申请 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmChgRequestMapper extends BaseMapper<PdmChgRequest> {
}
