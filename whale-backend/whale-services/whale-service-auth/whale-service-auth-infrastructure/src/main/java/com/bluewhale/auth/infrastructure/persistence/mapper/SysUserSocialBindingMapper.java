package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserSocialBindingPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 三方登录绑定 Mapper（权限中心扩展 M2）。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysUserSocialBindingMapper extends BaseMapper<SysUserSocialBindingPo> {
}
