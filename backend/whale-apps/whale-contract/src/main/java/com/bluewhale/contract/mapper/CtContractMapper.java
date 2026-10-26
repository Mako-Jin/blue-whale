package com.bluewhale.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.contract.entity.CtContract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 合同主表 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface CtContractMapper extends BaseMapper<CtContract> {
}
