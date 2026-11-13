package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysRoleConstraintPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色约束 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysRoleConstraintMapper extends BaseMapper<SysRoleConstraintPo> {
}
