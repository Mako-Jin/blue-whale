package com.bluewhale.pdm.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pdm.change.entity.PdmChgTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 变更追溯 Mapper
 *
 * <p>只提供新增与查询；项目内不新增更新/删除入口。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface PdmChgTraceMapper extends BaseMapper<PdmChgTrace> {
}
