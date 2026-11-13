package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserGroupPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户组 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysUserGroupMapper extends BaseMapper<SysUserGroupPo> {
}
