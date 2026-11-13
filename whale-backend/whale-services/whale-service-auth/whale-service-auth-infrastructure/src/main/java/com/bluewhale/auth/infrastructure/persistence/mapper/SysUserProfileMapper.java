package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserProfilePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户身份档案主表 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysUserProfileMapper extends BaseMapper<SysUserProfilePo> {
}
